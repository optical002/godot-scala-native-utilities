# `rx`

Port of the Rust `rx-rs` single-threaded push-based reactive library.

- `RxRef` / `RxVal` — reactive **cells**: hold state, dedup, emit immediately on
  subscribe. `RxSubject` / `RxObservable` — **streams**: no state, no immediate emit.
- **Lifetimes are tracker-only.** `subscribe(f)(using Tracker)`, with
  `DisposableTracker extends Tracker`. A derived container anchors its bridge
  subscription on the captured `Tracker`; there are no weak references.
- `map`/`flatMap` come from cats. The given instances take `(using Tracker)` and capture
  it **at summon-site**, because cats method signatures have no implicit slot — so users
  need `import cats.syntax.all.*` plus an in-scope `given Tracker`.
- Deliberately **not a `Monad`**: there is no lawful stack-safe `tailRecM` for a stateful
  switching cell.

Tests: `sbt rx/test` (29).
