import scala.scalanative.sbtplugin.ScalaNativePlugin
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport._

// ---------------------------------------------------------------------------
// Utilities: a single sbt build defining several Godot game-development
// libraries, each PUBLISHED ON ITS OWN (independent artifact, version, publish)
// and consumed by a downstream game project (../scala) via a normal
// `libraryDependencies` line.
//
// What a "godot library" here IS and IS NOT:
//   - IS a Scala Native library: it compiles to `.nir` and depends on the
//     `scala-native-gdextension` binding, so its node classes can extend Godot
//     engine classes. Its `.nir` is linked into the consuming game's `.so`.
//   - IS marked with a `gdext/godot-library.txt` resource so the consuming
//     project's plugin auto-registers its nodes (no consumer-side code).
//   - IS NOT a GDExtension: it does NOT apply GodotScalaNativePlugin, has no
//     `godotBuild`, no entry symbol, and never produces a `.so` itself.
//
// Add a new library = one `godotLibrary("name")` sub-project below.
// ---------------------------------------------------------------------------

lazy val scalaVersionStr = "3.8.1"

// The binding both these libraries and the game compile against. Pinned to the
// locally-published version (see ../../godot-scala-native, `sbt publishLocal`).
lazy val bindingVersion = "0.1.1-SNAPSHOT"

inThisBuild(
  Seq(
    organization := "io.github.optical002",
    version := "0.1.1-SNAPSHOT",
    scalaVersion := scalaVersionStr,
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT"))
  )
)

/**
 * Shared definition of a godot library sub-project living under
 * `modules/<name>`. Each is independently published.
 */
def godotLibrary(name0: String): Project =
  Project(name0, file(s"modules/$name0"))
    .enablePlugins(ScalaNativePlugin)
    .settings(
      name := name0,
      scalaVersion := scalaVersionStr,
      // The Godot language binding. `.cross(ScalaNativeCrossVersion.binary)` is
      // exactly what `%%%` expands to (applies the `_native0.5_3` suffix).
      libraryDependencies +=
        ("io.github.optical002" % "scala-native-gdextension" % bindingVersion)
          .cross(ScalaNativeCrossVersion.binary),
      // Marker resource: its presence in this library's MAIN jar tells a
      // consuming game project's plugin "scan my sources and auto-register my
      // nodes". The binding jar has no such marker, so only real libraries opt
      // in. Content is informational; only the file's presence matters.
      Compile / resourceGenerators += Def.task {
        val out =
          (Compile / resourceManaged).value / "gdext" / "godot-library.txt"
        IO.write(out, s"$name0\n")
        Seq(out)
      }.taskValue
    )

// Aggregating root — groups the libraries; never published itself.
lazy val root = (project in file("."))
  .aggregate(initSystem, rx, logicConstructor)
  .settings(
    name := "utilities",
    publish / skip := true
  )

lazy val initSystem = godotLibrary("init-system")
  .settings(
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val rx = godotLibrary("rx")
  .settings(
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.13.0",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val logicConstructor = godotLibrary("logic-constructor")
  .settings(
    // Scala-Native fork of pureconfig (optical002/pureconfig, `maven` branch; source on
    // scala-native-port), hosted as a raw-git Maven repo. Provides typed HOCON decoding via
    // `pureconfig.ConfigSource` + Scala 3 `derives ConfigReader`. Its backend is the SHocon
    // `com.typesafe.config` shim (org.akka-js:shocon-parser), pulled in transitively — the shocon
    // resolver below is required so that transitive dependency can be located.
    resolvers += "pureconfig-native" at
      "https://raw.githubusercontent.com/optical002/pureconfig/maven/maven",
    resolvers += "shocon-native" at
      "https://raw.githubusercontent.com/optical002/shocon/maven/maven",
    libraryDependencies += "com.github.pureconfig" %%% "pureconfig-core" % "1.0.0-native",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

