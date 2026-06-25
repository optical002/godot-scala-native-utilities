package rx

/** Convenient single-import surface, mirroring the Rust crate's `prelude`.
  *
  * {{{
  * import rx.prelude.*
  * }}}
  *
  * Re-exports the public types. Since they already live in the `rx` package,
  * `import rx.*` works equally well — `prelude` exists for parity with rx-rs.
  */
object prelude:
  export rx.{
    DisposableTracker,
    Tracker,
    RxRef,
    RxVal,
    RxSubject,
    RxObservable
  }
