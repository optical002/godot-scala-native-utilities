package prefabs

import scala.collection.mutable

import gdext.builtin.Dict
import gdext.classes.{Node, PackedScene, Resource}
import gdext.classes.instantiateAsChild // binding extra (PackedScene helper)
import gdext.internal.engine.{ClassMeta, Tres}
import gdext.internal.register.GodotScriptClass
import pureconfig.{ConfigCursor, ConfigReader}
import pureconfig.error.CannotConvert

import com.typesafe.config.{ConfigObject, ConfigValue, ConfigValueType}

/** Typed references to Godot prefab scenes, validated against the
  * `prefabs { … }` index that a codegen task generates from `prefabs.tres`.
  *
  * Game-agnostic (ported from the survivor-game `framework`): the `GameParser`
  * instances for `Prefab`/`PrefabGroup` are pureconfig `ConfigReader` factories
  * that close over a [[ParseCtx]] (pureconfig has no parse context), so config
  * types build their prefab readers with the ctx in scope.
  */

/** Godot resource mapping slash-form prefab paths (e.g. `"items/xp-bottle"`) to
  * their `PackedScene`s. Wired up in the editor (`prefabs.tres`, class
  * `Prefabs`) and assigned to the consuming entry node. The dictionary
  * key/value types match the `.tres`: `Dictionary[String, PackedScene]`. */
final class Prefabs(
  var scenes: Dict[String, Tres[PackedScene]] = Dict.empty
) extends Resource:

  /** The `PackedScene` registered at `path`, or `None` if absent. Godot returns
    * a nil object for a missing key, which decodes to an unassigned `Tres`. */
  def lookupScene(path: String): Option[PackedScene] =
    val tres = scenes(path)
    if tres.isAssigned then Some(tres.get) else None

/** A `PackedScene` whose root node is statically known to be `T`.
  *
  * Holds the validated prefab `path` and the editor-wired [[Prefabs]] resource;
  * the actual `PackedScene` is resolved lazily in [[instantiate]]. This
  * keeps ALL Godot calls out of config parsing (which may run on a background
  * thread — Godot APIs must run on the main thread), deferring the scene lookup
  * to init time on the main thread. */
final case class Prefab[T <: GodotScriptClass](path: String, prefabs: Prefabs)(using cls: ClassMeta[T]):
  /** Resolve the scene and add its root as a child of `parent`. Main thread. */
  def instantiate(parent: Node): T =
    packedScene.instantiateAsChild[T](parent)

  /** The `PackedScene`, resolved from the live `Prefabs` resource (main thread). */
  def packedScene: PackedScene =
    prefabs.lookupScene(path).getOrElse(
      throw RuntimeException(s"prefab '$path' has no PackedScene in the Prefabs resource"))

object Prefab:

  /** The Godot class name the prefab's root node must have. */
  def rootType[T](using cls: ClassMeta[T]): String = cls.className

  /** A `ConfigReader[Prefab[T]]` closed over `ctx`. Validates that the value is
    * a `{ path, type }` record, that the path is registered, and that its
    * recorded type matches `T`. Scene resolution is deferred to instantiation
    * (no Godot calls here — parsing may run off the main thread). */
  def reader[T <: GodotScriptClass](ctx: ParseCtx)(using cls: ClassMeta[T]): ConfigReader[Prefab[T]] =
    ConfigReader.fromCursor: cur =>
      cur.asObjectCursor.flatMap: obj =>
        parsePrefabRecord(obj, cur).flatMap: (path, recordType) =>
          val expected = rootType[T]
          resolve[T](ctx, path, recordType, expected, cur)

  /** Resolve a prefab by its exact registered path (used for derived paths like
    * `<map>/world`), validating registration and type. */
  def fromPath[T <: GodotScriptClass](path: String, ctx: ParseCtx)(using cls: ClassMeta[T]): Either[String, Prefab[T]] =
    val expected = rootType[T]
    ctx.knownPrefabs.get(path) match
      case Some(knownType) if knownType == expected => Right(Prefab[T](path, ctx.prefabs))
      case Some(knownType) => Left(s"prefab '$path' has type '$knownType', expected '$expected'")
      case None            => Left(s"prefab '$path' is not registered in prefabs.tres")

  private def resolve[T <: GodotScriptClass](
    ctx: ParseCtx,
    path: String,
    recordType: String,
    expected: String,
    cur: ConfigCursor
  )(using ClassMeta[T]): ConfigReader.Result[Prefab[T]] =
    ctx.knownPrefabs.get(path) match
      case Some(knownType) if knownType == recordType =>
        if recordType != expected then
          cur.failed(CannotConvert(path, "Prefab", s"expected prefab of type '$expected', got '$recordType' (path: $path)"))
        else Right(Prefab[T](path, ctx.prefabs))
      case Some(knownType) =>
        cur.failed(CannotConvert(path, "Prefab",
          s"prefab '$path' has inconsistent type metadata (record says '$recordType', prefabs index says '$knownType')"))
      case None =>
        cur.failed(CannotConvert(path, "Prefab", s"prefab '$path' is not registered in prefabs.tres"))

  /** Read `{ path = "...", type = "..." }` from an object cursor. */
  private def parsePrefabRecord(obj: pureconfig.ConfigObjectCursor, cur: ConfigCursor): ConfigReader.Result[(String, String)] =
    for
      pathCur <- obj.atKey("path")
      path <- pathCur.asString
      typeCur <- obj.atKey("type")
      tpe <- typeCur.asString
    yield (path, tpe)

/** Every prefab of type `T` whose registered path begins with a given prefix.
  * Parsed from a plain prefix string (e.g. `"map/forest"`). */
final case class PrefabGroup[T <: GodotScriptClass](prefabs: Vector[Prefab[T]]):
  def length: Int = prefabs.length
  def isEmpty: Boolean = prefabs.isEmpty
  def get(index: Int): Option[Prefab[T]] = prefabs.lift(index)
  def iterator: Iterator[Prefab[T]] = prefabs.iterator

object PrefabGroup:

  /** A `ConfigReader[PrefabGroup[T]]` closed over `ctx` (reads a prefix string). */
  def reader[T <: GodotScriptClass](ctx: ParseCtx)(using cls: ClassMeta[T]): ConfigReader[PrefabGroup[T]] =
    ConfigReader.fromCursor: cur =>
      cur.asString.flatMap: prefix =>
        fromPrefix[T](prefix, ctx) match
          case Right(g) => Right(g)
          case Left(msg) => cur.failed(CannotConvert(prefix, "PrefabGroup", msg))

  /** Collect every registered prefab of type `T` whose path starts with
    * `"<prefix>/"`, sorted by path (stable variant indices). Errors on a type
    * mismatch or an empty group. */
  def fromPrefix[T <: GodotScriptClass](prefix: String, ctx: ParseCtx)(using cls: ClassMeta[T]): Either[String, PrefabGroup[T]] =
    val expected = Prefab.rootType[T]
    val needle = s"${prefix.stripSuffix("/")}/"
    val matches = ctx.knownPrefabs.toList.filter((path, _) => path.startsWith(needle)).sortBy(_._1)

    val builder = Vector.newBuilder[Prefab[T]]
    var error: Option[String] = None
    matches.foreach: (path, recordType) =>
      if error.isEmpty then
        if recordType != expected then
          error = Some(s"prefab '$path' under group '$prefix' has type '$recordType', expected '$expected'")
        else
          Prefab.fromPath[T](path, ctx) match
            case Right(p)  => builder += p
            case Left(msg) => error = Some(msg)

    error match
      case Some(msg) => Left(msg)
      case None =>
        val prefabs = builder.result()
        if prefabs.isEmpty then Left(s"prefab group '$prefix' matched no prefabs under '$needle'")
        else Right(PrefabGroup(prefabs))

/** Everything a parser needs to resolve prefab references: the `path → root
  * type` index parsed from the generated `prefabs.conf`, and the editor-wired
  * [[Prefabs]] resource. */
final case class ParseCtx(knownPrefabs: Map[String, String], prefabs: Prefabs):
  def lookupScene(path: String): Option[PackedScene] =
    prefabs.lookupScene(path)

object ParseCtx:

  /** Build the `path → root type` index from the root config's `prefabs { … }`
    * section (absent section yields an empty index). Walks nested objects and
    * collects every typed leaf record `{ path, type }`. */
  def extractKnownPrefabs(prefabsSection: Option[ConfigValue]): Either[String, Map[String, String]] =
    prefabsSection match
      case None => Right(Map.empty)
      case Some(value) =>
        val out = mutable.Map.empty[String, String]
        collectLeaves(value, "prefabs", out).map(_ => out.toMap)

  private def collectLeaves(value: ConfigValue, field: String, out: mutable.Map[String, String]): Either[String, Unit] =
    value.valueType() match
      case ConfigValueType.OBJECT =>
        val obj = value.asInstanceOf[ConfigObject]
        val hasPath = obj.containsKey("path")
        val hasType = obj.containsKey("type")
        if hasPath && hasType then
          val path = obj.get("path").unwrapped.toString
          val tpe = obj.get("type").unwrapped.toString
          out(path) = tpe
          Right(())
        else
          var error: Option[String] = None
          val it = obj.entrySet().iterator()
          while it.hasNext && error.isEmpty do
            val e = it.next()
            collectLeaves(e.getValue, s"$field.${e.getKey}", out) match
              case Left(msg) => error = Some(msg)
              case Right(_)  => ()
          error.toLeft(())
      case other =>
        Left(s"'$field' entries must be prefab records { path, type } or nested objects, got: $other")
