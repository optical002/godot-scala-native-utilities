# rx

A lightweight single-threaded push-based reactive programming library for Scala
Native, built for Godot 4 games using the
[godot-scala-native](https://github.com/optical002/godot-scala-native) binding.

A Scala port of the Rust [`rx-rs`](https://github.com/optical002/rx-rs) crate.

## Architecture

### Core

|                 | Reactive Cell | Stream         |
| --------------- | ------------- | -------------- |
| **Read/Write**  | `RxRef`       | `RxSubject`    |
| **Read**        | `RxVal`       | `RxObservable` |

- **Reactive cell** (`RxRef`/`RxVal`) — holds a current value and notifies
  subscribers when it changes. Updates are **deduplicated** (`==`), and
  subscribing emits the current value immediately.
- **Stream** (`RxSubject`/`RxObservable`) — emits discrete events over time
  without holding state. Every emission is delivered (no dedup) and subscribing
  does **not** emit immediately — only events after subscription arrive.

### Lifetimes

- **`Tracker`** — the lifetime handle every subscription requires. Cannot be
  created directly.
- **`DisposableTracker`** — owns a `Tracker`; expose it via `.tracker`, clean up
  every tracked subscription with `dispose()`. `.tracker.track(child)` ties a
  child tracker's lifetime to a parent.

### Type relationships

```
RxRef    --.value-->       RxVal          RxVal  --.stream-->     RxObservable
RxRef    --.stream-->      RxObservable    RxVal  --.subscribe--> Subscription
RxSubject --.observable--> RxObservable    RxObservable --.toVal--> RxVal
RxSubject --.toVal-->      RxVal           RxObservable --.subscribe--> Subscription
DisposableTracker --.tracker--> Tracker   (Tracker required by every subscribe)
```

## Quick start

```scala
import rx.prelude.*  // or: import rx.*

val dt = DisposableTracker()

val counter = RxRef(0)
counter.value.subscribe(dt.tracker, v => println(s"Counter: $v")) // prints 0

counter.set(1) // prints 1
counter.set(2) // prints 2

dt.dispose()   // remove the subscription
```

## Operators

Cells (`RxRef`/`RxVal`):

- `map`, `flatMap`, `flatMapRef`, `flatMapObservable`, `flatMapSubject`
- `zipVal`, `zipRef`
- `stream` (cell → stream, dropping the immediate emission)

Streams (`RxSubject`/`RxObservable`):

- `map`, `flatMapVal`, `flatMapRef`, `flatMapObservable`, `flatMapSubject`
- `joinObservable`, `joinSubject`
- `toVal(initial, tracker)` (stream → cell)

`flatMap*` switches to the newest inner source on each change; `zip`/`join` emit
whenever either source changes.

## Port notes (vs. the Rust crate)

Rust's design carries the weight of the borrow checker; Scala Native's GC removes
most of it:

- **No `Rc<RefCell<…>>`.** Subscribers are kept in plain mutable buffers; the
  cell/stream is shared by reference. `set`/`next` snapshot the subscriber list
  before notifying, so a callback may add or remove subscriptions safely.
- **No `Drop`, no owner-count `Rc<()>`.** Subscription lifetimes are managed by
  explicit `DisposableTracker.dispose()` instead of drop semantics.
- **`Weak` → `java.lang.ref.WeakReference`.** Derived cells (`map`/`flatMap`/`zip`)
  keep their source subscription alive by holding the source tracker in a field,
  but the bridging subscription points back at the derived cell through a weak
  reference — so the source does not keep a derived cell alive. Once a derived
  cell is collected, its bridge becomes a no-op and disposes the source
  subscription, mirroring the Rust `Weak`-upgrade + `_lifetime_tracker` behaviour.
- **Method naming** follows Scala conventions: `value`/`observable` instead of
  `val()`/`observable()`, `flatMap` instead of `flat_map`, etc.

Behaviour is otherwise identical: immediate-on-subscribe for cells, no-immediate
for streams, dedup on cells, switch-on-change `flatMap`, emit-on-either
`zip`/`join`.

## Testing

Engine-independent — `src/test/scala`, run with `sbt rx/test` (munit on Scala
Native). `RxValSuite`, `TrackerSuite`, `OperatorsSuite` (28 tests) port the
rx-rs test suite and need no Godot runtime.

## License

MIT
