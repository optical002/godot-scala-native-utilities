# `init-system`

Plain Scala classes ("inits") receive Godot lifecycle callbacks without being nodes
themselves.

- `InitBase` takes `(using ParentId, InitContext)` and **derives `selfId`/`ctx` from
  those givens** — implementations must not declare them as their own fields.
- `MakeInit[I, P].initInner(params: P)(using ParentId, InitContext)` builds the init.
- `ParentId` is `opaque type ParentId <: InitId` — a real subtype, so a `using ParentId`
  never collides with the init's own `InitId` during implicit resolution.
- `makeAndRegister` rebinds `given ParentId = ParentId(id)` before calling `initInner`,
  which is what makes `selfId` resolve to the new init's own id.

Canonical shape for a factory + init pair: `SimpleUsageSuite` in this module's tests.

Engine-level tests must register from **inside** a live node's own lifecycle (e.g. its
`_ready`), not by poking a handle obtained from outside — which is why those tests live
in `scala/src/main/scala/initsystemtest` and are scene-driven.

Tests: `sbt init-system/test` (22).
