/** Game-agnostic framework layer ported from the Rust `framework` crate
  * (survivor-game), rebuilt on Scala Native + the `pureconfig` fork.
  *
  * The Rust crate defined its own `GameParser<Ctx>` typeclass and a
  * `#[derive(GameParser)]` proc-macro. Here those are subsumed by pureconfig's
  * `ConfigReader` and Scala 3's `derives ConfigReader`, so the port supplies
  * `given ConfigReader` instances rather than a parallel typeclass. The Rust
  * crate's dependencies map onto this repo's libraries: `hocon-rs` → the
  * pureconfig fork, `rx-rs` → the `rx` library, `godot` → `scala-native-gdextension`.
  *
  * Ported pieces:
  *   - [[godothoccon.Percentage]]     — `"80%"` percentages (`config/percentage.rs`)
  *   - [[godothoccon.RangeConfig]]    — inclusive int ranges (`config/range.rs`)
  *   - [[godothoccon.TimeSpan]]       — humantime durations (`config/time_span.rs`)
  *   - [[godothoccon.TimeSpanRange]]  — duration windows (`config/time_span_range.rs`)
  *   - [[godothoccon.ChanceTable]]    — random tables (`config/random_table.rs`)
  *   - [[godothoccon.ByPtr]]          — reference-equality wrapper (`config/by_ptr.rs`)
  *   - [[godothoccon.ColorConfig]]    — HTML-hex `Color` reader (`config/color.rs`)
  *   - [[godothoccon.Id]] / [[godothoccon.Registry]] / [[godothoccon.RegistryCtx]]
  *                                  — the id/registry directory pattern (`config/registry.rs`)
  *   - [[godothoccon.Loader]]         — config-file location/loading (`config/loader.rs`)
  *   - [[godothoccon.ConfigWatcher]]  — polling hot-reload (`config/watch.rs`)
  *   - [[godothoccon.XorShiftRng]]    — shareable gameplay rng (`rng.rs`)
  *
  * NOT ported:
  *   - `telemetry.rs` — the `tracing` telemetry layer (excluded per request).
  *   - `config/prefab.rs`, `config/curve.rs` — the Godot `Prefab`/`TweenCurve`
  *     parsers; the Scala-Native gdext binding lacks the `Tween` ease/transition
  *     enums, `interpolate_value`, and the `#[derive(GodotClass)]`/`#[export]`
  *     support those parsers require.
  *   - `utils.rs` — `instantiate_as_child`; the binding has no typed
  *     `Inherits<Node>` instantiation surface to port it against.
  */
package object godothoccon
