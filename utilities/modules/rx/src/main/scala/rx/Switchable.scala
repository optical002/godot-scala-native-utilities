package rx

/** Lets `RxVal.flatMap` switch onto any reactive source, choosing the result
  * container from the source:
  *   - a cell source (`RxVal`/`RxRef`) yields an `RxVal` (it has a current
  *     value, so the result is itself a cell);
  *   - a stream source (`RxObservable`/`RxSubject`) yields an `RxObservable`.
  *
  * `Out` is a match type on the source so the return type is inferred per call.
  */
trait Switchable[S[_]]:
  type Out[_]
  def switch[A, B](outer: RxVal[A], f: A => S[B])(using Tracker): Out[B]

object Switchable:
  type Aux[S[_], O[_]] = Switchable[S] { type Out[X] = O[X] }

  given Switchable.Aux[RxVal, RxVal] = new Switchable[RxVal]:
    type Out[X] = RxVal[X]
    def switch[A, B](outer: RxVal[A], f: A => RxVal[B])(using t: Tracker): RxVal[B] =
      switchCell(outer, f)

  given Switchable.Aux[RxRef, RxVal] = new Switchable[RxRef]:
    type Out[X] = RxVal[X]
    def switch[A, B](outer: RxVal[A], f: A => RxRef[B])(using t: Tracker): RxVal[B] =
      switchCell(outer, x => f(x).value)

  given Switchable.Aux[RxObservable, RxObservable] = new Switchable[RxObservable]:
    type Out[X] = RxObservable[X]
    def switch[A, B](outer: RxVal[A], f: A => RxObservable[B])(using t: Tracker): RxObservable[B] =
      switchStream(outer, f)

  given Switchable.Aux[RxSubject, RxObservable] = new Switchable[RxSubject]:
    type Out[X] = RxObservable[X]
    def switch[A, B](outer: RxVal[A], f: A => RxSubject[B])(using t: Tracker): RxObservable[B] =
      switchStream(outer, x => f(x).observable)

  // Cell -> cell: the result tracks the active inner cell's value, switching on
  // outer change. Anchored on the captured Tracker.
  private def switchCell[A, B](outer: RxVal[A], f: A => RxVal[B])(using t: Tracker): RxVal[B] =
    val initialInner = f(outer.get)
    val result = RxVal[B](initialInner.get)
    var innerTracker = DisposableTracker()
    t.track(innerTracker)

    def subscribeInner(in: RxVal[B]): Unit =
      in.subscribe(v => result.update(v))(using innerTracker)

    subscribeInner(initialInner)

    var lastOuter: A = outer.get
    outer.subscribe { o =>
      if lastOuter != o then
        lastOuter = o
        val newInner = f(o)
        innerTracker.dispose()
        innerTracker = DisposableTracker()
        t.track(innerTracker)
        result.update(newInner.get)
        subscribeInner(newInner)
    }
    result

  // Cell -> stream: emissions of the active inner stream flow through, switching
  // on outer change. A stream has no current value, so nothing is emitted on
  // switch itself.
  private def switchStream[A, B](outer: RxVal[A], f: A => RxObservable[B])(using t: Tracker): RxObservable[B] =
    val subject = RxSubject[B]()
    var inner = DisposableTracker()
    t.track(inner)
    outer.subscribe { o =>
      val newInner = f(o)
      inner.dispose()
      inner = DisposableTracker()
      t.track(inner)
      newInner.subscribe(v => subject.next(v))(using inner)
    }
    subject.observable
