lazy val game = (project in file("."))
  .enablePlugins(GodotScalaNativePlugin)
  .settings(
    name := "game",
    scalaVersion := "3.8.1",
    godotProjectDir := baseDirectory.value / ".." / "godot",
    // init-system (the library). Carries the godot-library marker, so the plugin
    // auto-registers InitSystemNode from its sources. The Godot-runtime tests for
    // it live locally under src/main/scala/initsystemtest (they need a live
    // engine), so they are scanned as ordinary game sources.
    libraryDependencies +=
      "io.github.optical002" %%% "init-system" % "0.1.1-SNAPSHOT"
  )

