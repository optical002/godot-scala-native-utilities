---
name: diagnose-registration
description: Diagnose why a Godot node class doesn't appear in the engine — "Cannot get class 'X'", the .so won't load, a scene fails to instantiate a Scala node, or a newly added node class isn't registered. Use when node registration or GDExtension loading fails.
---

# Diagnosing a registration failure

Node classes register **automatically**: the sbt plugin scans sources and emits
`Register.auto[...]` calls. There is no annotation and no registration list, so when a
node doesn't appear the fault is in the scan, the artifact, or the load — not in missing
boilerplate you forgot to write.

Work down this list in order; each step tells you which layer failed.

## 1. Was registration generated?

```
grep -n "Register.auto" scala/target/scala-3.8.1/src_managed/main/game/GeneratedRegistrations.scala
```

If your class is listed, the scan worked — skip to step 4.

## 2. If the class is absent — is it scannable?

The scan skips classes it cannot construct. Check that the class is **concrete** (not
abstract/trait) and that **every constructor parameter is constructible** by the
registrar. A node with required constructor args that the engine can't supply will be
silently filtered out.

## 3. If it's a *library* class — did its sources resolve?

Library nodes (from `utilities/modules/*`) are only scannable when the module's
`-sources.jar` resolves, opted in via the `gdext/godot-library.txt` marker resource.
Confirm:

```
grep "auto-register" godot/.scala/sbt.log
```

A healthy build logs `[auto-register] godot-library sources: <module>-sources.jar` for
each marker'd library, then `[auto-register] generated ...`. If your module's sources jar
is missing from that list, the artifact resolved without sources — republish it
(`cd utilities && sbt <module>/publishLocal`) and rebuild.

## 4. Did the engine load the extension?

```
cat godot/.scala/log        # godot/.scala/log.prev holds the previous run
```

Look for `registered game classes`. If that line is absent, the `.so` never loaded —
check for `GDExtension dynamic library not found` or `Error loading extension` in the
scene output.

## 5. Is the `.so` stale?

```
ls -la godot/.scala/libscala-native-gdextension.so godot/.scala/reload.stamp
```

If the `.so` predates your change, the build didn't reach the engine:

```
cd utilities && sbt <module>/publishLocal   # only if you changed a library module
cd scala && sbt godotBuild
```

Skipping the `publishLocal` does not error — it silently links the previous artifact.

## Reproducing the failure

Run the scene headless, and judge by **output, not exit code** — a run where every class
fails to load still exits 0:

```
cd godot && godot --headless --quit-after 120 manual_tests/<scene>.tscn 2>&1 \
  | grep -iE "cannot get class|error|failed"
```

Scenes: `init-system-manual-test`, `init-system-nested-test`, `config-watcher-test`,
`buff-demo-test`. Ignore `ld: warning ... missing .note.GNU-stack` lines that sbt tags
`[error]` — linker warnings, not failures.
