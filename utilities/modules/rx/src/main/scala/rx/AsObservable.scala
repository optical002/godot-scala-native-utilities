package rx

/** A reactive source that an `RxObservable.flatMap` can switch onto.
  *
  * `RxVal`/`RxRef` carry a current value (emitted immediately on each switch);
  * `RxObservable`/`RxSubject` have no current value, so only future emissions
  * flow through. The behaviour of `flatMap` therefore depends on the source.
  */
trait AsObservable[S[_]]:
  /** The source's current value, if it has one. */
  def current[B](source: S[B]): Option[B]

  /** Subscribe to the source's emissions, tied to the given tracker. For a
    * cell this fires immediately with the current value; for a stream it does
    * not (a stream has no current value).
    */
  def subscribeTo[B](source: S[B], f: B => Unit)(using Tracker): Unit

object AsObservable:
  def apply[S[_]](using ev: AsObservable[S]): AsObservable[S] = ev

  given AsObservable[RxVal] with
    def current[B](source: RxVal[B]): Option[B] = Some(source.get)
    def subscribeTo[B](source: RxVal[B], f: B => Unit)(using Tracker): Unit =
      source.subscribe(f)

  given AsObservable[RxRef] with
    def current[B](source: RxRef[B]): Option[B] = Some(source.get)
    def subscribeTo[B](source: RxRef[B], f: B => Unit)(using Tracker): Unit =
      source.value.subscribe(f)

  given AsObservable[RxObservable] with
    def current[B](source: RxObservable[B]): Option[B] = None
    def subscribeTo[B](source: RxObservable[B], f: B => Unit)(using Tracker): Unit =
      source.subscribe(f)

  given AsObservable[RxSubject] with
    def current[B](source: RxSubject[B]): Option[B] = None
    def subscribeTo[B](source: RxSubject[B], f: B => Unit)(using Tracker): Unit =
      source.observable.subscribe(f)
