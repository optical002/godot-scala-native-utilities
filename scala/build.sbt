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
      "com.github.optical002.godot-scala-native-utilities" %%% "init-system" % "0.1.6",
    // godot-hoccon (package `godothoccon`): HOCON config value types, the
    // pureconfig-based Loader, and the polling ConfigWatcher. Used by game.config
    // to load and hot-reload config/ at runtime. It depends transitively on rx
    // (the reactive library that powers reloads); the HOCON backend (pureconfig
    // + shocon) is vendored INSIDE the module jar (see ../vendor), so no extra
    // resolvers are needed here.
    libraryDependencies +=
      "com.github.optical002.godot-scala-native-utilities" %%% "godot-hoccon" % "0.1.6",
    // logic-constructor (package `logicconstructor`): the data-driven action layer
    // plus the generic buff lifecycle (logicconstructor.buffs). The buff demo
    // (game.buffs + BuffDemoNode) exercises it against a harness-local stat model.
    libraryDependencies +=
      "com.github.optical002.godot-scala-native-utilities" %%% "logic-constructor" % "0.1.6"
  )

