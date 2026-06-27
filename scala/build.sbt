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
      "io.github.optical002" %%% "init-system" % "0.1.1-SNAPSHOT",
    // godot-hoccon (package `godothoccon`): HOCON config value types, the
    // pureconfig-based Loader, and the polling ConfigWatcher. Used by game.config
    // to load and hot-reload config/ at runtime. It depends transitively on rx
    // (the reactive library that powers reloads) and on the Scala-Native
    // pureconfig fork, whose raw-git Maven repos must be declared here so the
    // transitive HOCON backend (pureconfig + shocon) resolves.
    resolvers += "pureconfig-native" at
      "https://raw.githubusercontent.com/optical002/pureconfig/maven/maven",
    resolvers += "shocon-native" at
      "https://raw.githubusercontent.com/optical002/shocon/maven/maven",
    libraryDependencies +=
      "io.github.optical002" %%% "godot-hoccon" % "0.1.1-SNAPSHOT"
  )

