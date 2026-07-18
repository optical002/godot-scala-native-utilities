# CLAUDE.md

Workbench of publishable Godot game-development libraries built on a Scala Native
binding. Scala 3.8.1 / Scala Native 0.5.10 / Godot 4.6.1 / munit 1.3.3.

- `utilities/` — the published libraries (sbt). Five Scala Native modules
  (`init-system`, `rx`, `godot-hoccon`, `prefabs`, `logic-constructor`) plus
  `sbt-godot-hoccon` (a JVM sbt plugin, Scala 2.12). **`utilities/build.sbt` is heavily
  commented** — read it, don't re-derive it.
- `scala/` — the consumer test game. Consumes the libraries as **published artifacts**,
  not source `ProjectRef`s. `sbt godotBuild` produces the `.so`.
- `godot/` — the Godot 4.6 project. `manual_tests/*.tscn` are the engine-level tests.
- `vendor/`, `config/` — see `vendor/README.md` and `config/application.conf`.

Each module carries its own `CLAUDE.md` (`utilities/modules/<name>/CLAUDE.md`), loaded
only when you touch that module's files. Same for `config/`.
Skills: `release` (cutting a version), `diagnose-registration` (node won't load).

**Adding a node = writing the class.** A module's `gdext/godot-library.txt` marker makes
the consuming game's plugin scan its sources and emit registration — no annotation, no
registration list, no consumer-side code. A manual test scene is a 3-line `.tscn` naming
the class as `type=`.

## Resolving the binding

Versions are plain semver. The binding version comes from `bindingVersion` in
`utilities/build.sbt` (reads `VERSION`, falling back to the next release version) and
resolves from JitPack, with local ivy taking precedence when present.

The fallback names the version being worked *toward*, so it does not exist on JitPack
until that tag is pushed — until then it resolves only from a local `publishLocal` of the
binding. A resolution failure means the pinned version is in neither place.

**`~/.ivy2/local/io.github.optical002/` is a decoy** — leftovers from the pre-0.1.1 group
rename that look like valid local publishes but are never resolved. Only
`~/.ivy2/local/com.github.optical002.*/` counts.

## Publishing to the game

```
cd utilities && sbt <module>/publishLocal
cd scala && sbt godotBuild
```

Skipping the `publishLocal` does not error — it silently links the previous artifact. So
if `scala/` fails to compile against an API you can see in `utilities/modules/*`, suspect
a stale artifact before suspecting the code.

## Reading sbt output

Two ways this build lies to you:

- **`[error]` lines that are not errors.** Scala Native links through `ld`, whose
  warnings go to stderr and get tagged `[error]` by sbt (e.g. `missing .note.GNU-stack`,
  for the wrong architecture at that). The run still ends `[success]`. Never conclude
  failure from grepping `error]` — trust the final summary line and the munit counts.
- **Aggregate `sbt test` OOMs** on the default heap. Use `sbt -J-Xmx4G test`; per-module
  tasks are fine without it.

## Verification

```
cd utilities && sbt init-system/test        # 22
cd utilities && sbt rx/test                 # 29
cd utilities && sbt godot-hoccon/test       # 31
cd utilities && sbt prefabs/test            #  8
cd utilities && sbt logic-constructor/test  # 37
cd utilities && sbt -J-Xmx4G test           # 127 total

cd godot && godot --headless --quit-after 120 manual_tests/init-system-manual-test.tscn
```

Headless runs have two counter-intuitive rules:

- **Scenes never self-terminate** — no scene calls `quit()`, so always pass
  `--quit-after N` (N = frames).
- **The exit code proves nothing.** A run with the `.so` missing, where *every* class
  fails to load, still exits **0**. Judge by output:
  `... 2>&1 | grep -iE "cannot get class|error|failed"` — a healthy run matches nothing
  and prints the scene's own lines.

A clean compile is not proof for engine-dependent code; only a clean headless run is.
Engine-level init-system tests live in `scala/src/main/scala/initsystemtest` (they need a
live engine, so they ship as ordinary game sources).
