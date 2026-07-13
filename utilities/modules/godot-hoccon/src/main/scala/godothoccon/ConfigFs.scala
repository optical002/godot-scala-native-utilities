package godothoccon

import java.io.File

import gdext.classes.{DirAccess, FileAccess}

/** File access for config loading that works both on the real filesystem (dev,
  * loose `config/` dir) and inside an exported Godot PCK (`res://config/…`,
  * read via the engine's `FileAccess`/`DirAccess`).
  *
  * A path is treated as a Godot virtual path when it starts with `res://` or
  * `user://`; otherwise it is a normal OS path handled with `java.io.File`.
  * This lets a game bundle (and hide) its config inside the PCK while keeping
  * hot-reloadable loose files during development.
  */
object ConfigFs:

  def isGodotPath(path: String): Boolean =
    path.startsWith("res://") || path.startsWith("user://")

  /** True if a file exists at `path` (OS path or `res://`). */
  def exists(path: String): Boolean =
    if isGodotPath(path) then FileAccess.fileExists(path)
    else File(path).exists()

  /** Read the whole file at `path` as UTF-8 text, or `None` if it is absent /
    * unreadable. */
  def readText(path: String): Option[String] =
    if isGodotPath(path) then
      if FileAccess.fileExists(path) then
        val t = FileAccess.getFileAsString(path)
        if t == null then None else Some(t)
      else None
    else
      val f = File(path)
      if !f.exists() then None
      else
        try
          val src = scala.io.Source.fromFile(f, "UTF-8")
          try Some(src.mkString)
          finally src.close()
        catch case _: Throwable => None

  /** List the `.conf` file stems directly under `dir` (OS path or `res://`),
    * sorted for deterministic ordering. `None` if the directory can't be read. */
  def listConfNames(dir: String): Option[List[String]] =
    val names =
      if isGodotPath(dir) then listGodotDir(dir)
      else
        Option(File(dir).listFiles())
          .map(_.toList.filter(_.isFile).map(_.getName))
    names.map: fileNames =>
      fileNames.filter(_.endsWith(".conf")).map(_.stripSuffix(".conf")).sorted

  /** Join a base config path with a subpath, preserving res://; always ends the
    * base with a single '/'. */
  def join(base: String, sub: String): String =
    val b = if base.endsWith("/") then base else base + "/"
    b + sub

  private def listGodotDir(dir: String): Option[List[String]] =
    val da = DirAccess.open(dir)
    if da == null then None
    else
      da.listDirBegin()
      val buf = scala.collection.mutable.ListBuffer.empty[String]
      var n = da.getNext()
      while n != null && n.nonEmpty do
        if !da.currentIsDir() then buf += n
        n = da.getNext()
      da.listDirEnd()
      Some(buf.toList)
