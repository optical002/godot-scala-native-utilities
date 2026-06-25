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

// ---------------------------------------------------------------------------
// Library: init-system — lifecycle/initialization management for game objects.
// A Scala port of the Rust `init-system` crate (godot-rust-utils), redesigned to
// use Scala's inheritance + plain mutable collections instead of Rust's
// trait-juggling and Rc<RefCell> borrow-checker workarounds.
//
// Engine-independent tests live under src/test and run via `sbt init-system/test`
// (munit on Scala Native). Tests that need a live Godot runtime live in the
// separate `init-system-engine-test` library below.
// ---------------------------------------------------------------------------
lazy val initSystem = godotLibrary("init-system")
  .settings(
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

// The Godot-runtime tests for init-system (Node-backed factory, input dispatch)
// live in the consuming project (../scala/src/main/scala/initsystemtest) and run
// in-engine — they need a live Godot, so they sit with the game sources rather
// than as a separate published library here.

// ---------------------------------------------------------------------------
// Library: rx — a single-threaded push-based reactive programming library.
// Originally a Scala port of the Rust `rx-rs` crate, since redesigned around the
// Typelevel ecosystem. Core types: RxRef/RxVal (reactive cells with a current
// value + deduplicated updates) and RxSubject/RxObservable (event streams
// without state). Each public type is a minimal trait with its implementation in
// the companion `apply`.
//
// Operators come from cats typeclasses: Functor + FlatMap (and a custom Apply
// whose `product` is emit-on-either) for RxVal, Functor + FlatMap for
// RxObservable. The instances take a `using Tracker`, so `import cats.syntax.all.*`
// plus an in-scope Tracker enables `map`/`flatMap`/`tupled`/`mapN`. zip/join and
// the cross-type bridges (stream/toVal/flatMapVal/…) stay as trait methods.
//
// Lifetimes are tracker-only: a derived container anchors its bridge subscription
// on the in-scope `using Tracker`, so disposing that tracker tears the graph down
// (no Rc<RefCell>/Weak/`_lifetime_tracker` as in the Rust original). Tracker is a
// trait and DisposableTracker <: Tracker. Behaviour matches the original:
// immediate-on-subscribe for cells, no-immediate for streams, dedup on cells,
// switch-on-change flatMap, emit-on-either join/zip.
//
// Engine-independent tests live under src/test and run via `sbt rx/test`.
// ---------------------------------------------------------------------------
lazy val rx = godotLibrary("rx")
  .settings(
    libraryDependencies += "org.typelevel" %%% "cats-core" % "2.13.0",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

// ---------------------------------------------------------------------------
// Library: logic-constructor — move combat/ability logic out of code and into
// config: a list of typed actions ("deal 15 damage", "heal 4") each gated by a
// collision rule (Self / SameKind / Other), run from a source entity against a
// target. A Scala port of the Rust `logic-constructor` crate.
//
// Port note: the Rust crate parsed HOCON via the `hocon-rs` crate's `Value`
// type. No HOCON parser publishes a Scala-Native-0.5 + Scala-3 artifact
// (pureconfig is JVM-only; shocon's only native build is native0.3/Scala-2.11),
// so this module carries its own tiny `ConfigValue` ADT (Str/Num/Obj/Arr) and
// ports every parser against it. Consumers build a `ConfigValue` however they
// like (Godot `ConfigFile`/JSON, a hand-written reader, or literals in tests).
//
// The Rust `Box<dyn LcAction<T>>` + `clone_box` dance is gone — Scala traits are
// reference types, so `LcAction[T]` is just a trait and configs hold plain
// references. `CollisionKind` is a small bit-flag value class instead of the
// `bitflags!` macro. Behaviour matches: simple-form defaults to OTHER, full form
// requires both `lca` and `collision`, run_lca fires self/same-kind/other arms.
//
// Engine-independent tests live under src/test and run via
// `sbt logic-constructor/test`.
// ---------------------------------------------------------------------------
lazy val logicConstructor = godotLibrary("logic-constructor")
  .settings(
    // Scala-Native port of SHocon's HOCON parser, hosted as a raw-git Maven repo
    // on GitHub (optical002/shocon, `maven` branch; source on scala3-native-port).
    // Used by hoconConfigParser.scala to parse HOCON into this module's ConfigValue.
    resolvers += "shocon-native" at
      "https://raw.githubusercontent.com/optical002/shocon/maven/maven",
    libraryDependencies += "org.akka-js" %%% "shocon-parser" % "1.0.0-native",
    libraryDependencies += "org.scalameta" %%% "munit" % "1.3.3" % Test,
    testFrameworks += new TestFramework("munit.Framework")
  )

// Future libraries (publish on their own) — add as needed:
// lazy val mathLib  = godotLibrary("math-lib")
// ...and aggregate them in `root` above.
