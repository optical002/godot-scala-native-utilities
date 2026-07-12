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

// The binding both these libraries and the game compile against. On a JitPack
// release build (JitPack exports VERSION=<tag>) we pin the binding released
// under the SAME tag — release godot-scala-native first, then tag this repo
// with the same version. Locally the fallback is the NEXT release dev version
// (a `-SNAPSHOT`), published by `sbt publishLocal` in ../../godot-scala-native
// (same coordinates; local ivy wins over JitPack, and the version doesn't exist
// on JitPack until its tag is pushed).
lazy val bindingVersion = sys.env.getOrElse("VERSION", "0.1.3-SNAPSHOT")

inThisBuild(
  Seq(
    // Publishing goes through JitPack (repo-root jitpack.yml runs
    // `sbt publishM2` on a pushed plain-semver tag). The organization is the
    // exact JitPack group for this repo, so publishLocal yields the same
    // coordinates as the released artifacts.
    organization := "com.github.optical002.godot-scala-native-utilities",
    version := sys.env.getOrElse("VERSION", "0.1.3-SNAPSHOT"),
    scalaVersion := scalaVersionStr,
    licenses := Seq("MIT" -> url("https://opensource.org/licenses/MIT")),
    // Lets the `scala-native-gdextension` binding resolve from JitPack when it
    // is not in the local ivy repo (which is consulted first, so local
    // publishLocal artifacts always win during co-development).
    resolvers += "jitpack" at "https://jitpack.io"
  )
)

// ---------------------------------------------------------------------------
// Vendored HOCON backend (../vendor). The Scala-Native pureconfig fork and its
// SHocon backend used to come from raw-git Maven repos that every consumer had
// to declare as resolvers. Instead the two jars live in ../vendor and their
// contents (classes + NIR) are REPACKAGED into each hocon-using module's
// published jar, so consumers need no resolver beyond the usual ones. The
// vendored jars carry no POM, so their Maven-Central transitives are
// re-declared here (versions taken from the forks' POMs at 1.0.0-native).
// ---------------------------------------------------------------------------
lazy val vendoredHoconJars = Def.setting {
  (((ThisBuild / baseDirectory).value / ".." / "vendor") * "*.jar").get.sorted
}

lazy val vendoredHocon: Seq[Setting[_]] = Seq(
  Compile / unmanagedJars ++= vendoredHoconJars.value.map(Attributed.blank),
  Test / unmanagedJars ++= vendoredHoconJars.value.map(Attributed.blank),
  libraryDependencies ++= Seq(
    // pureconfig-core's typesafe-config API (shimmed natively by shocon).
    "com.typesafe" % "config" % "1.4.9",
    // shocon-parser's compile deps.
    ("org.scala-lang.modules" % "scala-collection-compat" % "2.12.0")
      .cross(ScalaNativeCrossVersion.binary),
    ("com.lihaoyi" % "fastparse" % "3.1.1")
      .cross(ScalaNativeCrossVersion.binary)
  ),
  // Merge the vendored jars' contents into this module's published jar.
  Compile / packageBin / mappings ++= {
    val outBase = target.value / "vendored-hocon"
    vendoredHoconJars.value.flatMap { jar =>
      val dest = outBase / jar.getName.stripSuffix(".jar")
      IO.unzip(jar, dest)
      (dest ** "*").get
        .filter(_.isFile)
        .filterNot(f => IO.relativize(dest, f).exists(_.startsWith("META-INF")))
        .map(f => f -> IO.relativize(dest, f).get)
    }
  }
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
        ("com.github.optical002.godot-scala-native" % "scala-native-gdextension" % bindingVersion)
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
      }.taskValue,
      libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
      testFrameworks += new TestFramework("munit.Framework")
    )

// Aggregating root — groups the libraries; never published itself.
lazy val root = (project in file("."))
  .aggregate(initSystem, rx, logicConstructor, godotHoccon, prefabs, sbtGodotHoccon)
  .settings(
    name := "utilities",
    publish / skip := true
  )

// The sbt plugin owning the HOCON codegen (prefabs.conf, *-ids.conf,
// animations.conf) a consuming game runs at build time. Pure JVM (Scala 2.12,
// sbt 1.x) — NOT a godot library: no ScalaNativePlugin, no binding dependency.
// It lives here (not in the godot-scala-native binding repo) because prefab/
// hocon config generation is a utilities concern; the binding stays free of it.
lazy val sbtGodotHoccon = (project in file("modules/sbt-godot-hoccon"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-godot-hoccon",
    // sbt 1.9.x runs on Scala 2.12.
    scalaVersion := "2.12.20",
    // Publish with the fully cross-versioned artifact filename
    // (`sbt-godot-hoccon_2.12_1.0-<ver>.jar`) so it matches the Maven
    // coordinate path — required for sbt to resolve the plugin from JitPack
    // (which serves the `publishM2` output verbatim).
    sbtPluginPublishLegacyMavenStyle := false
  )

lazy val initSystem = godotLibrary("init-system")

lazy val rx = godotLibrary("rx")
  .settings(
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.13.0"
  )

// godot-hoccon (package `godothoccon`): a game-agnostic config layer ported from
// the Rust `framework` crate (survivor-game) — HOCON config value types + the
// id/registry directory pattern. HOCON decoding is via the Scala-Native
// pureconfig fork (same backend as logic-constructor); the reactive registry
// cell reuses the `rx` library. It also depends on the `gdext` binding (added by
// `godotLibrary` for every module here), so config types may reference Godot
// builtins (`gdext.builtin.Color`, …). The `tracing` telemetry layer was not
// ported; the Godot tween `curve` parser IS ported here ([[TweenCurveConfig]],
// evaluating Godot's easing equations directly), and the `prefab` flow lives in
// its own `prefabs` module (below).
lazy val godotHoccon = godotLibrary("godot-hoccon")
  .dependsOn(rx)
  .settings(vendoredHocon)

// prefabs (package `prefabs`): the game-agnostic typed-prefab flow extracted from
// the survivor-game `framework` — the `Prefabs` resource (a `Dictionary[String,
// PackedScene]`), the `Prefab[T]`/`PrefabGroup[T]` pureconfig readers, and the
// `ParseCtx` carrying the generated `path → root type` index. Needs the binding
// (PackedScene/Resource/Dict/Tres + the `PackedScene.instantiateAsChild` helper)
// and the pureconfig fork; reuses nothing from godot-hoccon, so it depends only
// on the binding (via `godotLibrary`). The companion codegen that emits
// `prefabs.conf` from `prefabs.tres` is provided by the sbt plugin (see
// language-binding-scala/sbt-godot-scala-native).
lazy val prefabs = godotLibrary("prefabs")
  .settings(vendoredHocon)

lazy val logicConstructor = godotLibrary("logic-constructor")
  .dependsOn(godotHoccon)
  .settings(vendoredHocon)

