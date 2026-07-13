package godothoccon

import java.io.File

import pureconfig.error.{CannotConvert, ConfigReaderFailures, ConvertFailure}
import pureconfig.{ConfigReader, ConfigSource}

/** Config-file location + loading helpers.
  *
  * Ported from `framework/src/config/loader.rs`. The Rust original threaded a
  * HOCON classpath through every load so `include` directives resolved against
  * the config directory; pureconfig's `ConfigSource.file` resolves `include`
  * relative to the included file's own directory, so the explicit classpath
  * plumbing is unnecessary here.
  */
object Loader:

  val ConfigFileName: String = "application.conf"

  /** Candidate config roots, searched in order. The loose filesystem paths are
    * tried first (they support hot-reload in development); `res://config/` is a
    * fallback so an exported game can load config bundled — and hidden — inside
    * its PCK, independent of the process working directory. */
  val ConfigDirectoryPaths: List[String] =
    List("../../config/", "../config/", "config/", "res://config/")

  /** Locate `<root>/<subdir>/application.conf` among the known config roots and
    * decode it as `A`. Returns the directory it was found in alongside the value.
    */
  def loadAndParse[A](configSubdirectory: String)(using
    ConfigReader[A]
  ): Either[String, (A, String)] =
    ConfigDirectoryPaths
      .map(base => (base, ConfigFs.join(ConfigFs.join(base, configSubdirectory), ConfigFileName)))
      .find((_, file) => ConfigFs.exists(file)) match
      case Some((base, file)) =>
        ConfigFs.readText(file) match
          case None => Left(s"failed to read $file")
          case Some(text) =>
            ConfigSource.string(text).load[A]
              .map(a => (a, ConfigFs.join(base, configSubdirectory)))
              .left.map(_.prettyPrint())
      case None =>
        Left(
          s"Config file not found in subdirectory '$configSubdirectory'. Searched: " +
            ConfigDirectoryPaths.map(p => ConfigFs.join(ConfigFs.join(p, configSubdirectory), ConfigFileName)).mkString("[", ", ", "]")
        )

  /** Find the config root directory itself as a path string (OS path or
    * `res://config/`). Used by callers that then load/list within it. */
  def findConfigRoot(): Either[String, String] =
    ConfigDirectoryPaths
      .find(p => ConfigFs.exists(ConfigFs.join(p, ConfigFileName)))
      .toRight(s"Config directory not found. Searched: ${ConfigDirectoryPaths.mkString("[", ", ", "]")}")

  /** Find the config root as a `File` — filesystem roots only. Used by the
    * hot-reload watcher, which is a development-time (loose-files) feature and
    * does not apply to a `res://` PCK build. */
  def findConfigDirectory(): Either[String, File] =
    ConfigDirectoryPaths
      .filterNot(ConfigFs.isGodotPath)
      .map(File(_))
      .find(d => d.exists() && d.isDirectory)
      .toRight(s"Config directory not found. Searched: ${ConfigDirectoryPaths.filterNot(ConfigFs.isGodotPath).mkString("[", ", ", "]")}")

  /** Decode a single `<configDir>/<subdir>/<name>.conf` file as `A` (OS path or
    * `res://`). */
  def loadConf[A](configDir: String, subdir: String, name: String)(using
    ConfigReader[A]
  ): Either[ConfigReaderFailures, A] =
    val path = ConfigFs.join(ConfigFs.join(configDir, subdir), s"$name.conf")
    ConfigFs.readText(path) match
      case None => Left(ConfigReaderFailures(ConvertFailure(CannotConvert(path, "config", s"file not found: $path"), None, "")))
      case Some(text) => ConfigSource.string(text).load[A]

  /** File-based overload for backward compatibility. */
  def loadConf[A](configDir: File, subdir: String, name: String)(using
    ConfigReader[A]
  ): Either[ConfigReaderFailures, A] =
    loadConf(configDir.getPath, subdir, name)
