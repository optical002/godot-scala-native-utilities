package rx

/** A read-write stream of events.
  *
  * `RxSubject` adds write access on top of [[RxObservable]]: `next` emits an event
  * to all subscribers. Like an observable it holds no current value. Expose a
  * read-only view with `.observable`.
  *
  * {{{
  * val clicks = RxSubject[Int]()
  * val dt = DisposableTracker()
  * clicks.observable.subscribe(dt.tracker, n => println(s"clicked \$n")) // nothing yet
  * clicks.next(1) // prints 1
  * }}}
  */
final class RxSubject[T] private (private val inner: RxObservable[T]):

  /** Emits an event to all current subscribers. */
  def next(value: T): Unit = inner.emit(value)

  /** A read-only view of this stream. */
  def observable: RxObservable[T] = inner

  /** Number of active subscribers. */
  def subscriberCount: Int = inner.subscriberCount

  /** See [[RxObservable.toVal]]. */
  def toVal(initial: T, tracker: Tracker): RxVal[T] = inner.toVal(initial, tracker)

  /** See [[RxObservable.map]]. */
  def map[B](f: T => B): RxObservable[B] = inner.map(f)

  /** See [[RxObservable.flatMapVal]]. */
  def flatMapVal[B](f: T => RxVal[B]): RxObservable[B] = inner.flatMapVal(f)

  /** See [[RxObservable.flatMapRef]]. */
  def flatMapRef[B](f: T => RxRef[B]): RxObservable[B] = inner.flatMapRef(f)

  /** See [[RxObservable.flatMapObservable]]. */
  def flatMapObservable[B](f: T => RxObservable[B]): RxObservable[B] =
    inner.flatMapObservable(f)

  /** See [[RxObservable.flatMapSubject]]. */
  def flatMapSubject[B](f: T => RxSubject[B]): RxObservable[B] =
    inner.flatMapSubject(f)

  /** See [[RxObservable.joinObservable]]. */
  def joinObservable(other: RxObservable[T]): RxObservable[T] =
    inner.joinObservable(other)

  /** See [[RxObservable.joinSubject]]. */
  def joinSubject(other: RxSubject[T]): RxObservable[T] = inner.joinSubject(other)

object RxSubject:
  /** Creates a new empty `RxSubject`. */
  def apply[T](): RxSubject[T] = new RxSubject[T](RxObservable[T]())
