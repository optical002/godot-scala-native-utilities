# `logic-constructor`

Config-driven combat/ability actions, ported from Rust.

**No HOCON parser publishes a Scala-Native-0.5 + Scala-3 artifact.** The module therefore
carries its own `ConfigValue` ADT (`ConfigValue.scala`) rather than depending on one.
`CObj` is an ordered `Seq[(String, ConfigValue)]` — the ordering is load-bearing, since
single-key effect parsing must be deterministic.

Do not reach for a JVM HOCON library here; it will not link under Scala Native.

Depends on `godot-hoccon`. Tests: `sbt logic-constructor/test` (37).
