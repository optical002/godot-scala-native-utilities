package godotutilities

import sbt._
import sbt.Keys._
import sbt.nio.Keys.{fileInputs, watchOnFileInputEvent}
import sbt.nio.Watch
import sbt.nio.file.{Glob, RecursiveGlob}

/**
 * sbt AutoPlugin owning the HOCON config generation for a Godot Scala Native
 * game: `prefabs.conf` (from `prefabs.tres`), the `*-ids.conf` registries (from
 * the asset config dirs), and optionally `animations.conf` (from the prefab
 * scenes' AnimationLibrary GLBs). It complements — and does not require — the
 * binding's `GodotScalaNativePlugin`, which stays free of hocon/prefab
 * concerns.
 *
 * {{{
 *   // project/plugins.sbt
 *   addSbtPlugin("com.github.optical002.godot-scala-native-utilities" % "sbt-godot-hoccon" % "<version>")
 *
 *   // build.sbt
 *   enablePlugins(GodotHocconPlugin)
 *   godotPrefabsTres    := Some(baseDirectory.value / ".." / "godot" / "resources" / "prefabs.tres")
 *   godotIdConfs        := Seq(PrefabsCodegen.IdConf("game/assets/enemies", "assets/enemy-ids.conf", "enemy-ids"))
 *   godotAnimationsConf := Some("animations.conf")
 * }}}
 */
object GodotHocconPlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin
  override def trigger  = noTrigger

  object autoImport {
    val godotPrefabsTres = settingKey[Option[File]](
      "Godot `prefabs.tres` resource to generate `prefabs.conf` from (None disables prefab codegen)"
    )
    val godotPrefabsDir = settingKey[File](
      "Root dir holding the prefab .tscn files referenced by prefabs.tres (the res://prefabs/ mount)"
    )
    val godotGeneratedDir = settingKey[File](
      "Output dir for the generated prefabs.conf + id confs (e.g. config/generated)"
    )
    val godotConfigDir = settingKey[File](
      "Base config dir the id-conf subdirs are resolved against (e.g. config)"
    )
    val godotIdConfs = settingKey[Seq[PrefabsCodegen.IdConf]](
      "Id-bearing config dirs to emit `<name>-ids.conf` for (subdir, outFile, hocon section)"
    )
    val godotAnimationsConf = settingKey[Option[String]](
      "Generated animation-names conf filename (e.g. Some(\"animations.conf\")); None disables animations codegen"
    )
    val genConfig = taskKey[Unit](
      "Regenerate the hocon config (prefabs.conf + *-ids.conf + animations conf) from prefabs.tres, asset dirs, and scenes"
    )

    /** Re-export so a consuming `build.sbt` can write `PrefabsCodegen.IdConf(...)`
      * without importing `godotutilities`. */
    val PrefabsCodegen = godotutilities.PrefabsCodegen
  }

  import autoImport._

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Codegen is opt-in: a game points `godotPrefabsTres` at its `prefabs.tres`
    // and lists its id dirs in `godotIdConfs`. Left unset, `genConfig` is a
    // no-op (a project may have no prefabs).
    godotPrefabsTres    := None,
    godotPrefabsDir     := baseDirectory.value / ".." / "godot" / "prefabs",
    godotGeneratedDir   := baseDirectory.value / ".." / "config" / "generated",
    godotConfigDir      := baseDirectory.value / ".." / "config",
    godotIdConfs        := Seq.empty,
    godotAnimationsConf := None,

    genConfig := {
      // Reference the input globs so the `~` watch discovers them: sbt only
      // registers `fileInputs` that a task's definition actually uses (unlike
      // `watchTriggers`, which are collected per-scope unconditionally).
      val _   = (genConfig / fileInputs).value
      val log = streams.value.log
      godotPrefabsTres.value match {
        case None => () // codegen disabled for this project
        case Some(tres) =>
          PrefabsCodegen.run(
            prefabsTres  = tres,
            prefabsDir   = godotPrefabsDir.value,
            generatedDir = godotGeneratedDir.value,
            configDir    = godotConfigDir.value,
            idConfs      = godotIdConfs.value,
            log          = msg => log.info(msg)
          )
          godotAnimationsConf.value.foreach { outName =>
            AnimationsCodegen.run(
              prefabsTres  = tres,
              prefabsDir   = godotPrefabsDir.value,
              generatedDir = godotGeneratedDir.value,
              outFileName  = outName,
              log          = msg => log.info(msg)
            )
          }
      }
    },

    // Keep the generated config in sync on every compile (the prefab module
    // reads it at runtime), mirroring the old per-game build.sbt wiring.
    Compile / compile := (Compile / compile).dependsOn(genConfig).value,

    // Make the sbt watch (`~godotBuild` / `~compile`) see genConfig's real
    // inputs — the id-bearing asset dirs, prefabs.tres, and (when animations
    // codegen is on) the scene/GLB files the animation names come from. Without
    // this the watch only covers Scala sources, so adding/removing an asset
    // .conf never triggered a build and the generated confs went stale until
    // the next code edit. Declared as `fileInputs` (hash-stamped), not
    // `watchTriggers` (mtime-stamped), so the event handler below gets reliable
    // Creation/Deletion/Update classification.
    genConfig / fileInputs ++= {
      val configDir = godotConfigDir.value.getCanonicalFile
      val idGlobs   = godotIdConfs.value.map(idc => Glob(configDir / idc.subdir, "*.conf"))
      val tresGlob  = godotPrefabsTres.value.map(tres => Glob(tres.getCanonicalFile)).toSeq
      val animGlobs =
        if (godotAnimationsConf.value.isEmpty) Seq.empty
        else {
          val prefabsDir = godotPrefabsDir.value.getCanonicalFile
          val godotRoot  = prefabsDir.getParentFile
          Seq(
            Glob(prefabsDir, RecursiveGlob / "*.tscn"),
            Glob(godotRoot / "assets", RecursiveGlob / "*.tscn"),
            Glob(godotRoot / "assets", RecursiveGlob / "*.glb")
          )
        }
      idGlobs ++ tresGlob ++ animGlobs
    },

    // Content edits of existing asset confs are hot-reloaded by the running
    // game; only the file SET (create/delete/rename) feeds the id codegen. So
    // ignore Update events on the id dirs — otherwise every gameplay-tuning
    // save would relink and hot-swap the .so for nothing. Scene/GLB content
    // edits DO trigger (they change the generated animation names).
    watchOnFileInputEvent := {
      val configDir = godotConfigDir.value.getCanonicalFile
      val idGlobs   = godotIdConfs.value.map(idc => Glob(configDir / idc.subdir, "*.conf"))
      (_: Int, event: Watch.Event) =>
        event match {
          case _: Watch.Update if idGlobs.exists(_.matches(event.path)) => Watch.Ignore
          case _                                                        => Watch.Trigger
        }
    }
  )
}
