# logic-constructor

Move combat and ability logic out of code and into config: a list of typed
actions ("deal 15 damage", "heal 4"), each gated by a *collision rule*, run from
a source entity against a target. Designers tweak damage, healing, and targeting
in data; the engine parses it into typed actions and runs them — no per-ability
glue code.

Built for Godot 4 games using the
[godot-scala-native](https://github.com/optical002/godot-scala-native) binding.
A Scala port of the Rust
[`logic-constructor`](https://github.com/optical002/rust-logic-constructor) crate.

## Concepts

- **`LcEntityType`** — your entity "registry" (a Scala `enum`/sealed trait)
  extends this and returns a distinct `typeId` per kind. Same `typeId` ⇒ "same
  kind".
- **`LcEntity[T]`** — a thin wrapper around one game entity, carrying its type id.
- **`LcAction[T]`** — one action; implement `apply(source, target)`.
- **`CollisionKind`** — a bit-flag set deciding when an action fires:
  `Self`, `SameKind`, `Other` (combinable with `|`).
- **`LcSingleActionConfig[T]`** — one action + its collision rule.
- **`LcActionConfig[T]`** — an ordered list of those (one logical ability).
- **`runLca(config, source, target)`** — runs every entry, firing the arms its
  `CollisionKind` selects.
- **`ConfigValue`** — the parser surface: a tiny `Str/Num/Bool/Obj/Arr` value
  tree the parsers consume (see **Port notes**).

## Usage

```scala
import logicconstructor.prelude.*

// 1. Define your entity registry
enum GameEntity extends LcEntityType:
  case Player(hp: Health)
  case Enemy(hp: Health)
  def typeId: LcEntityTypeId = this match
    case _: Player => 1
    case _: Enemy  => 2

// 2. Define actions
final case class DealDamage(amount: Double) extends LcAction[GameEntity]:
  def apply(source: LcEntity[GameEntity], target: LcEntity[GameEntity]): Unit =
    target.gameEntity match { case e: Player => e.hp.value -= amount; case e: Enemy => e.hp.value -= amount }

// 3. Build a config in code …
val attack = LcActionConfig(Vector(
  LcSingleActionConfig(DealDamage(15.0), CollisionKind.Other)
))
runLca(attack, LcEntity(player), LcEntity(enemy))

// 4. … or parse one from config data
val cfg: ConfigValue = ConfigValue.arr(
  ConfigValue.obj("DealDamage" -> ConfigValue.CNum(15)),
  ConfigValue.obj(
    "lca" -> ConfigValue.obj("Heal" -> ConfigValue.CNum(4)),
    "collision" -> ConfigValue.CStr("Self")
  )
)
val parseEffect: ParseEffect[GameEntity] = ???   // turns a CObj into an LcAction
val parsed: Either[String, LcActionConfig[GameEntity]] =
  parseLcActionConfig(cfg, parseEffect)
```

## Config forms

Each entry in the list is one of:

- **Simple** — `{ DealDamage: 15 }`: the whole object is the effect; collision
  defaults to `Other`.
- **Full** — `{ lca: { Heal: 4 }, collision: "Self" }`: `lca` is the effect,
  `collision` is parsed via `parseCollisionKind` (`"Self"`, `"SameKind"`,
  `"Other"`, or piped: `"Self | Other"`).

The library never interprets the effect body — you supply a `ParseEffect[T]`
closure that turns it into a concrete `LcAction[T]`. This is the only
effect-type knowledge in the pipeline.

## Port notes (vs. the Rust crate)

- **No HOCON dependency.** The Rust crate threaded `hocon-rs`'s `Value` through
  every parser. No HOCON parser publishes a Scala-Native-0.5 + Scala-3 artifact
  (pureconfig is JVM-only; shocon's only native build is native0.3/Scala-2.11),
  so this module carries its own tiny `ConfigValue` ADT and ports every parser
  against it. Consumers obtain a `ConfigValue` however they like — from a Godot
  `ConfigFile`/JSON read, a hand-written reader, or literals (as the tests do).
- **No `Box<dyn LcAction>` / `clone_box`.** Those existed to clone trait objects
  past the borrow checker. Scala traits are reference types, so `LcAction[T]` is
  just a trait and configs hold plain references — the whole `clone_box`
  apparatus is gone.
- **`CollisionKind`** is an opaque `Int` bit-flag set with `|`/`contains`,
  replacing the Rust `bitflags!` macro.
- **`Result<_, String>` → `Either[String, _]`.** Parsers return `Either`; error
  strings (index propagation, "expects an array", "unknown effect", …) match the
  Rust messages so consumers can pattern-match the same way.
- **`From` impls → the `LcEntity(_)` constructor.** Rust's per-type `From`
  conversions are just `LcEntity(gameEntity)`.

Behaviour is otherwise identical: simple form defaults to `Other`, full form
requires both `lca` and `collision`, `runLca` fires the self / same-kind / other
arms exactly as the Rust `run_lca`.

## Testing

Engine-independent — `src/test/scala`, run with `sbt logic-constructor/test`
(munit on Scala Native). `RunLcaSuite` and `ParserSuite` port the Rust test
suite (`basic_usage.rs` + the parser unit tests), built against `ConfigValue`
literals instead of HOCON text.

## License

MIT
