package game.config

import gdext.classes.Node
import gdext.api.GodotPrint

import godothoccon.ConfigWatcher
import rx.{DisposableTracker, RxRef, Tracker}

/** Godot entry node that hot-reloads the repo-root `config/` directory.
  *
  * Drop it into a scene (see `godot/manual_tests/config-watcher-test.tscn`) and
  * on `_ready` it:
  *   1. locates `config/` (searching `../../config/`, `../config/`, `config/`),
  *   2. loads `config/application.conf` into a typed [[GameConfig]] and logs the
  *      FULL initial config,
  *   3. holds that config in an `rx.RxRef` and subscribes to it — the
  *      subscription is what logs reloads, printing only the fields that changed.
  *
  * Each frame `_process` polls the framework [[ConfigWatcher]]; when any file
  * under `config/` is touched it reparses and pushes the new value into the
  * `RxRef`. The `rx` library is the reload bus: gameplay code elsewhere could
  * `subscribe`/`map` the same `config.value` to react to changes without
  * knowing anything about files.
  *
  * Modelled on survivor-game's `entry` crate, which wired a `notify` watcher
  * into a `ReactiveGameConfig` of `RxRef`s the exact same way.
  */
final class ConfigWatcherNode extends Node:

  // Subscriptions live as long as the node; this tracker is never disposed
  // because the watcher runs for the whole session.
  private given Tracker = DisposableTracker()

  // Populated once the initial load succeeds. `None` means we never started
  // (e.g. the config directory was missing), so `_process` does nothing.
  private var config: Option[RxRef[GameConfig]] = None
  private var watcher: Option[ConfigWatcher] = None

  // The last config we reported on; the reload diff is computed against it so
  // each log line shows exactly what moved since the previous snapshot.
  private var lastLogged: GameConfig = scala.compiletime.uninitialized

  override def _ready(): Unit =
    ConfigLoader.load() match
      case Left(err) =>
        GodotPrint.print(s"[config] failed to load initial config: $err")
      case Right(initial) =>
        lastLogged = initial
        GodotPrint.print(s"[config] loaded initial config from config/:\n${GameConfig.describe(initial)}")

        val ref = RxRef(initial)
        config = Some(ref)

        // The reload logger. RxRef emits immediately on subscribe; that first
        // emission is the initial config we already printed in full, so skip it
        // and only diff-log subsequent changes.
        var first = true
        ref.subscribe { current =>
          if first then first = false
          else logChanges(current)
        }

        ConfigWatcher.create(() => reload(ref)) match
          case Left(err) =>
            GodotPrint.print(s"[config] could not start watcher: $err")
          case Right(w) =>
            watcher = Some(w)
            GodotPrint.print("[config] watching config/ for changes...")

  override def _process(delta: Double): Unit =
    watcher.foreach(_.pollAndApply())

  /** Re-read config/ and, if it parses, push it into the RxRef (which fires the
    * diff-logging subscription). Parse errors keep the previous config. */
  private def reload(ref: RxRef[GameConfig]): Unit =
    ConfigLoader.load() match
      case Left(err) =>
        GodotPrint.print(s"[config] reload failed, keeping previous config: $err")
      case Right(next) =>
        ref.set(next)

  private def logChanges(next: GameConfig): Unit =
    val changes = GameConfig.diff(lastLogged, next)
    lastLogged = next
    if changes.isEmpty then
      GodotPrint.print("[config] reloaded — no field values changed")
    else
      val body = changes.map((k, o, n) => s"  $k: $o -> $n").mkString("\n")
      GodotPrint.print(s"[config] reloaded — ${changes.length} field(s) changed:\n$body")
