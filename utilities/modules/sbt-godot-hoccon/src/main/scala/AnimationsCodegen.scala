package godotutilities

import java.io.File
import java.nio.file.Files

/** Build-time generation of `<generatedDir>/<outFileName>` (e.g.
  * `animations.conf`): for every prefab registered in `prefabs.tres`, walks its
  * `.tscn` (and, recursively, every instanced PackedScene `.tscn`) looking for
  * `libraries/<name> = ExtResource("...")` AnimationLibrary assignments, then
  * reads the clip names out of each `.glb` library's JSON chunk. Emits one
  * section per prefab (keyed by the last segment of its prefab key):
  *
  * {{{
  * animations {
  *   warrior {
  *     sword-attack = "ual/Sword_Attack"
  *   }
  * }
  * }}}
  *
  * Values are the fully-qualified `<library>/<Clip>` names an
  * `AnimationPlayer`/`AnimationTree` resolves at runtime. Binary AnimationLibrary
  * resources (`.res`) cannot be parsed and are skipped with a warning.
  */
object AnimationsCodegen {

  private val MaxSceneDepth = 4

  def run(
    prefabsTres: File,
    prefabsDir: File,
    generatedDir: File,
    outFileName: String,
    log: String => Unit = Console.err.println
  ): Unit = {
    val godotRoot = prefabsDir.getCanonicalFile.getParentFile

    val entries: Seq[PrefabsCodegen.PrefabEntry] =
      PrefabsCodegen.readToString(prefabsTres).toSeq.flatMap { text =>
        PrefabsCodegen.extractPrefabEntries(text, prefabsDir).getOrElse(Seq.empty)
      }

    val sections =
      scala.collection.mutable.TreeMap.empty[String, scala.collection.mutable.TreeMap[String, String]]

    for (entry <- entries; tscn <- entry.tscnAbsPath) {
      val libs = collectAnimationLibraries(tscn, godotRoot)
      if (libs.nonEmpty) {
        val sectionName = kebab(entry.key.split('/').last)
        if (sections.contains(sectionName))
          log(s"animations codegen: duplicate section '$sectionName' (prefab '${entry.key}'); first wins")
        else {
          val clips = scala.collection.mutable.TreeMap.empty[String, String]
          for ((libName, libFile) <- libs) {
            if (libFile.getName.endsWith(".glb")) {
              readGlbAnimationNames(libFile) match {
                case Some(names) =>
                  for (n <- names) {
                    val key = kebab(n)
                    if (key.isEmpty)
                      log(s"animations codegen: clip '$n' in $libName produces an empty key; skipped")
                    else if (clips.contains(key))
                      log(s"animations codegen: clip key '$key' collides in section '$sectionName'; first wins")
                    else clips(key) = s"$libName/$n"
                  }
                case None =>
                  log(s"animations codegen: failed to parse GLB ${libFile.getPath} (prefab '${entry.key}')")
              }
            } else
              log(s"animations codegen: skipping non-GLB AnimationLibrary ${libFile.getPath} (prefab '${entry.key}')")
          }
          if (clips.nonEmpty) sections(sectionName) = clips
        }
      }
    }

    val body = new StringBuilder
    body.append(
      "# AUTO-GENERATED from prefab scenes' AnimationLibrary GLBs by the sbt-godot-hoccon plugin.\n" +
        "# Do not edit by hand. Edit the scenes/animation libraries and rebuild.\n" +
        "animations {\n"
    )
    for ((section, clips) <- sections) {
      body.append("  ").append(section).append(" {\n")
      for ((k, v) <- clips)
        body.append("    ").append(k).append(" = \"").append(v).append("\"\n")
      body.append("  }\n")
    }
    body.append("}\n")

    PrefabsCodegen.writeIfChanged(new File(generatedDir, outFileName), body.toString)
  }

  /** Walk `rootTscn` and its instanced `.tscn` PackedScenes, collecting every
    * `libraries/<name> = ExtResource("<id>")` AnimationLibrary assignment as
    * `(library name, resolved file)`. */
  private def collectAnimationLibraries(rootTscn: File, godotRoot: File): Seq[(String, File)] = {
    val visited = scala.collection.mutable.Set.empty[String]
    val out     = scala.collection.mutable.ArrayBuffer.empty[(String, File)]

    def walk(tscn: File, depth: Int): Unit = {
      if (depth > MaxSceneDepth || !visited.add(tscn.getCanonicalPath)) return
      PrefabsCodegen.readToString(tscn).foreach { text =>
        val animLibs = PrefabsCodegen.collectExtResources(text, "AnimationLibrary")
        val scenes   = PrefabsCodegen.collectExtResources(text, "PackedScene")

        for (raw <- text.linesIterator) {
          val line = raw.trim
          if (line.startsWith("libraries/")) {
            val eq = line.indexOf('=')
            if (eq > "libraries/".length) {
              val name = line.substring("libraries/".length, eq).trim
              for {
                id   <- PrefabsCodegen.extractExtResourceId(line.substring(eq))
                res  <- animLibs.get(id)
                file <- resToFile(res, godotRoot)
              } out += ((name, file))
            }
          }
        }

        for {
          res <- scenes.values.toSeq.sorted
          if res.endsWith(".tscn")
          file <- resToFile(res, godotRoot)
        } walk(file, depth + 1)
      }
    }

    walk(rootTscn, 0)
    out.toSeq
  }

  private def resToFile(resPath: String, godotRoot: File): Option[File] = {
    val prefix = "res://"
    if (!resPath.startsWith(prefix)) None
    else Some(new File(godotRoot, resPath.substring(prefix.length)))
  }

  private[godotutilities] def kebab(s: String): String =
    s.trim.toLowerCase.replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "")

  /** Read the animation clip names from a binary GLB: verify the `glTF` magic,
    * take chunk 0 (must be `JSON`), and extract `animations[].name`. */
  private[godotutilities] def readGlbAnimationNames(glb: File): Option[Seq[String]] = {
    val bytes =
      try Files.readAllBytes(glb.toPath)
      catch { case _: java.io.IOException => return None }
    if (bytes.length < 20) return None

    def u32(off: Int): Long =
      (bytes(off) & 0xffL) |
        ((bytes(off + 1) & 0xffL) << 8) |
        ((bytes(off + 2) & 0xffL) << 16) |
        ((bytes(off + 3) & 0xffL) << 24)

    if (u32(0) != 0x46546c67L) return None // "glTF"
    val chunkLen = u32(12)
    if (u32(16) != 0x4e4f534aL) return None // "JSON"
    if (chunkLen < 0 || 20L + chunkLen > bytes.length) return None

    val json = new String(bytes, 20, chunkLen.toInt, "UTF-8")
    Some(extractAnimationNames(json))
  }

  /** Pull `animations[].name` out of the glTF JSON chunk with a dependency-free
    * scanner: walk the root object's top-level keys, bracket-match past values
    * we don't care about, and inside the `animations` array capture each
    * element object's own top-level `name`. */
  private[godotutilities] def extractAnimationNames(json: String): Seq[String] = {
    val out = scala.collection.mutable.ArrayBuffer.empty[String]
    val n   = json.length
    var i   = 0

    def skipWs(): Unit = while (i < n && json.charAt(i).isWhitespace) i += 1

    def parseString(): String = { // caller guarantees json.charAt(i) == '"'
      val sb = new StringBuilder
      i += 1
      var done = false
      while (i < n && !done) {
        json.charAt(i) match {
          case '"' => i += 1; done = true
          case '\\' if i + 1 < n =>
            json.charAt(i + 1) match {
              case 'n' => sb.append('\n'); i += 2
              case 't' => sb.append('\t'); i += 2
              case 'r' => sb.append('\r'); i += 2
              case 'b' => sb.append('\b'); i += 2
              case 'f' => sb.append('\f'); i += 2
              case 'u' if i + 5 < n =>
                try sb.append(Integer.parseInt(json.substring(i + 2, i + 6), 16).toChar)
                catch { case _: NumberFormatException => () }
                i += 6
              case c => sb.append(c); i += 2
            }
          case c => sb.append(c); i += 1
        }
      }
      sb.toString
    }

    def skipContainer(open: Char, close: Char): Unit = {
      var depth = 0
      var inStr = false
      var esc   = false
      var done  = false
      while (i < n && !done) {
        val c = json.charAt(i)
        if (esc) esc = false
        else if (inStr) { if (c == '\\') esc = true else if (c == '"') inStr = false }
        else if (c == '"') inStr = true
        else if (c == open) depth += 1
        else if (c == close) { depth -= 1; if (depth == 0) done = true }
        i += 1
      }
    }

    def skipValue(): Unit = {
      skipWs()
      if (i < n) json.charAt(i) match {
        case '"' => parseString(); ()
        case '{' => skipContainer('{', '}')
        case '[' => skipContainer('[', ']')
        case _   => while (i < n && !",]}".contains(json.charAt(i))) i += 1
      }
    }

    def scanAnimationsArray(): Unit = { // json.charAt(i) == '['
      i += 1
      var done = false
      while (i < n && !done) {
        skipWs()
        if (i >= n) done = true
        else json.charAt(i) match {
          case ']' => i += 1; done = true
          case ',' => i += 1
          case '{' =>
            i += 1
            var objDone            = false
            var name: Option[String] = None
            while (i < n && !objDone) {
              skipWs()
              if (i >= n) objDone = true
              else json.charAt(i) match {
                case '}' => i += 1; objDone = true
                case ',' => i += 1
                case '"' =>
                  val key = parseString()
                  skipWs()
                  if (i < n && json.charAt(i) == ':') i += 1
                  skipWs()
                  if (key == "name" && i < n && json.charAt(i) == '"') name = Some(parseString())
                  else skipValue()
                case _ => i += 1
              }
            }
            name.foreach(out += _)
          case _ => skipValue()
        }
      }
    }

    skipWs()
    if (i < n && json.charAt(i) == '{') {
      i += 1
      var done = false
      while (i < n && !done) {
        skipWs()
        if (i >= n) done = true
        else json.charAt(i) match {
          case '}' => done = true
          case ',' => i += 1
          case '"' =>
            val key = parseString()
            skipWs()
            if (i < n && json.charAt(i) == ':') i += 1
            skipWs()
            if (key == "animations" && i < n && json.charAt(i) == '[') {
              scanAnimationsArray()
              done = true
            } else skipValue()
          case _ => i += 1
        }
      }
    }
    out.toSeq
  }
}
