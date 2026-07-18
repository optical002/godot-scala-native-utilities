# Changelog — `prefabs`

Versions are plain-semver git tags of the
[godot-scala-native-utilities](https://jitpack.io/#optical002/godot-scala-native-utilities)
repo, built by JitPack under the group
`com.github.optical002.godot-scala-native-utilities` (all modules share one
tag). The topmost *Unreleased* section collects changes for the next release;
its version appears on JitPack only once that tag is pushed.

## [Unreleased]

_No changes yet._

## [0.1.2] — 2026-07-12

### Changed
- The HOCON backend (the Scala-Native `pureconfig` + `shocon` forks) is now
  **vendored inside this jar** (see the repo's `vendor/`): classes + NIR are
  repackaged into the published artifact and its POM carries only
  Maven-Central deps (`com.typesafe:config`, `scala-collection-compat`,
  `fastparse`). **Consumers no longer need the raw-git
  `pureconfig-native`/`shocon-native` resolvers.**

## [0.1.1] — 2026-07-06

First JitPack release (group moved from `io.github.optical002` to
`com.github.optical002.godot-scala-native-utilities`).

Typed-prefab flow: `Prefabs` resource, `Prefab[T]`/`PrefabGroup[T]` pureconfig readers, `ParseCtx` path→type index (codegen provided by the sbt plugin).
