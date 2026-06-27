package godothoccon

import java.io.File

import pureconfig.error.ConfigReaderFailures
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

  /** Candidate config roots, searched in order (matches the Rust constant). */
  val ConfigDirectoryPaths: List[String] = List("../../config/", "../config/", "config/")

  /** Locate `<root>/<subdir>/application.conf` among the known config roots and
    * decode it as `A`. Returns the directory it was found in alongside the value.
    */
  def loadAndParse[A](configSubdirectory: String)(using
    ConfigReader[A]
  ): Either[String, (A, File)] =
    val attempts = ConfigDirectoryPaths.map: base =>
      val path = s"$base$configSubdirectory$ConfigFileName"
      (File(path), File(s"$base$configSubdirectory"))
    attempts.find(_._1.exists()) match
      case Some((file, dir)) =>
        ConfigSource.file(file.getPath).load[A].map(a => (a, dir)).left.map(_.prettyPrint())
      case None =>
        Left(
          s"Config file not found in subdirectory '$configSubdirectory'. Searched: " +
            ConfigDirectoryPaths.map(p => s"$p$configSubdirectory$ConfigFileName").mkString("[", ", ", "]")
        )

  /** Find the config root directory itself (used by the hot-reload watcher). */
  def findConfigDirectory(): Either[String, File] =
    ConfigDirectoryPaths
      .map(File(_))
      .find(d => d.exists() && d.isDirectory)
      .toRight(s"Config directory not found. Searched: ${ConfigDirectoryPaths.mkString("[", ", ", "]")}")

  /** Decode a single `<configDir>/<subdir>/<name>.conf` file as `A`. */
  def loadConf[A](configDir: File, subdir: String, name: String)(using
    ConfigReader[A]
  ): Either[ConfigReaderFailures, A] =
    ConfigSource.file(File(File(configDir, subdir), s"$name.conf").getPath).load[A]
