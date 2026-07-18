# `prefabs`

The typed-prefab flow: the `Prefabs` resource (a `Dictionary[String, PackedScene]`), the
`Prefab[T]` / `PrefabGroup[T]` pureconfig readers, and the `ParseCtx` carrying the
generated `path → root type` index.

Decodes through the vendored Scala-Native HOCON backend — see `vendor/README.md`. The
companion codegen that emits `prefabs.conf` from `prefabs.tres` lives in the
`sbt-godot-hoccon` plugin; nothing in this repo enables it yet.

Depends only on the binding (no `rx`, no `godot-hoccon`).
Tests: `sbt prefabs/test` (8).
