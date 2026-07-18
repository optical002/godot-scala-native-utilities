# `godot-hoccon`

The game-agnostic config layer: HOCON config types, the id/registry directory pattern,
and a Godot tween `curve` parser (evaluating Godot's easing equations directly).

Decodes through the vendored Scala-Native HOCON backend — see `vendor/README.md` for what
is vendored and how to upgrade it. The `vendoredHocon` settings in `utilities/build.sbt`
repackage that backend into this module's published jar.

Depends on `rx`. Tests: `sbt godot-hoccon/test` (31).
