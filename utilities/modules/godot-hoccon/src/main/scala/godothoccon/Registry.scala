package godothoccon

import java.io.File

import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure}
import pureconfig.{ConfigReader, ConfigSource}
import rx.RxRef

/** A config type whose instances live one-per-file in a config subdirectory.
  * Provide a `given RegistryItem[C]` to opt `C` into the [[Registry]] loader;
  * everything else (directory walk, per-file parsing, id validation) is shared.
  *
  * Ported from the `RegistryItem` trait in `framework/src/config/registry.rs`.
  */
trait RegistryItem[C]:
  /** Subdirectory under the config root, e.g. `"game/assets/enemies"`. */
  def subdir: String

object RegistryItem:
  def apply[C](using ev: RegistryItem[C]): RegistryItem[C] = ev

  def at[C](dir: String): RegistryItem[C] =
    new RegistryItem[C]:
      val subdir: String = dir

/** Identifier of a registry entry — the config file stem, phantom-tagged by the
  * config type it identifies: `Id[EnemyConfig]("skeleton")`. Equality and
  * hashing are by the string value alone (the phantom type is erased).
  *
  * Ported from the `Id<C>` type in `framework/src/config/registry.rs`.
  */
final case class Id[C](value: String):
  override def toString: String = s"Id($value)"

object Id:

  /** Parse an id from a plain string, without validating it against a known
    * set. Use [[validatedReader]] when the set of valid ids is available.
    */
  given [C]: ConfigReader[Id[C]] =
    ConfigReader.fromCursor(_.asString.map(Id[C](_)))

  /** Parse an id and validate it against a known set (the Rust original did
    * this through the parse context's `known_ids`). `subdir` is only used to
    * make the error message match the original.
    */
  def validatedReader[C](knownIds: Set[Id[C]], subdir: String): ConfigReader[Id[C]] =
    ConfigReader.fromCursor: cur =>
      cur.asString.flatMap: s =>
        val id = Id[C](s)
        if knownIds.contains(id) then Right(id)
        else
          cur.failed(
            CannotConvert(
              s,
              "Id",
              s"'$s' is not a known $subdir id (known: ${knownIds.map(_.value).mkString("{", ", ", "}")})"
            )
          )

/** A map from [[Id]] to the parsed config of type `C`, built by
  * [[Registry.load]] from a directory of `.conf` files.
  *
  * Ported from the `Registry<C>` type in `framework/src/config/registry.rs`.
  */
final case class Registry[C](entries: Map[Id[C], C]):
  def get(id: Id[C]): Option[C] = entries.get(id)
  def contains(id: Id[C]): Boolean = entries.contains(id)
  def ids: Iterable[Id[C]] = entries.keys
  def size: Int = entries.size

object Registry:
  def empty[C]: Registry[C] = Registry(Map.empty)

  /** List the `.conf` file stems in `dir`, sorted for deterministic ordering. */
  def collectConfNames(dir: File): Either[String, List[String]] =
    Option(dir.listFiles())
      .toRight(s"failed to read config dir ${dir.getPath}")
      .map: files =>
        files.toList
          .filter(f => f.isFile && f.getName.endsWith(".conf"))
          .map(_.getName.stripSuffix(".conf"))
          .sorted

  /** Load every `.conf` file under `<configDir>/<subdir>`, parse each through
    * `C`'s [[ConfigReader]], and collect them into a [[Registry]] keyed by file
    * stem.
    *
    * Mirrors `parse_registry` from the Rust original (minus the reactive
    * `RxRef` registry cell, which [[RegistryCtx]] models separately).
    */
  def load[C](configDir: File)(using
    item: RegistryItem[C],
    reader: ConfigReader[C]
  ): Either[ConfigReaderFailures, Registry[C]] =
    val subdirFile = File(configDir, item.subdir)
    collectConfNames(subdirFile) match
      case Left(msg) =>
        Left(ConfigReaderFailures(asFailure(msg)))
      case Right(names) =>
        val loaded: List[Either[ConfigReaderFailures, (Id[C], C)]] =
          names.map: name =>
            ConfigSource
              .file(File(subdirFile, s"$name.conf").getPath)
              .load[C]
              .map(cfg => Id[C](name) -> cfg)
        loaded.collectFirst { case Left(f) => f } match
          case Some(failures) => Left(failures)
          case None => Right(Registry(loaded.collect { case Right(kv) => kv }.toMap))

  private def asFailure(msg: String): ConvertFailure =
    ConvertFailure(CannotConvert(msg, "Registry", msg), None, "")

/** Per-registry state: the full id set and a reactive [[rx.RxRef]] holding the
  * current [[Registry]]. The id set is built from the directory listing alone,
  * so it exists *before* any config body is parsed — entries can cross-reference
  * each other's ids freely, and consumers can capture the `RxRef` for deferred
  * lookups (it's empty until [[Registry.load]] fills it).
  *
  * Ported from `RegistryCtx` in `framework/src/config/registry.rs` (the
  * `notify`-driven reload that re-`set`s the ref lives in [[ConfigWatcher]]).
  */
final case class RegistryCtx[C](knownIds: Set[Id[C]], registry: RxRef[Registry[C]])

object RegistryCtx:

  /** List the `.conf` files under `<configDir>/<subdir>` and build the id set;
    * the registry starts empty and is filled by [[discoverAndLoad]] (or any
    * later reload).
    */
  def discover[C](configDir: File)(using item: RegistryItem[C]): Either[String, RegistryCtx[C]] =
    val subdirFile = File(configDir, item.subdir)
    Registry.collectConfNames(subdirFile).map: names =>
      RegistryCtx(names.map(Id[C](_)).toSet, RxRef(Registry.empty[C]))

  /** Discover the ids, load every config body, push the result into the ctx's
    * reactive ref, and return the ctx.
    */
  def discoverAndLoad[C](configDir: File)(using
    RegistryItem[C],
    ConfigReader[C]
  ): Either[String, RegistryCtx[C]] =
    for
      ctx <- discover(configDir)
      reg <- Registry.load(configDir).left.map(_.prettyPrint())
    yield
      ctx.registry.set(reg)
      ctx
