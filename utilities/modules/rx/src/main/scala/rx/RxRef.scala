package rx

/** A read-write reactive cell holding a current value.
  *
  * `RxRef` adds write access on top of [[RxVal]]: `set`/`modify` update the value
  * and notify subscribers (deduplicated by `==`). Expose a read-only view with
  * `.value`.
  *
  * {{{
  * val counter = RxRef(0)
  * val dt = DisposableTracker()
  * counter.value.subscribe(dt.tracker, v => println(s"Counter: \$v")) // prints 0
  * counter.set(1) // prints 1
  * }}}
  */
final class RxRef[T] private (private val inner: RxVal[T]):

  /** Sets a new value, notifying subscribers if it changed. */
  def set(value: T): Unit = inner.update(value)

  /** The current value. */
  def get: T = inner.get

  /** A read-only view of this cell. */
  def value: RxVal[T] = inner

  /** Updates the value via `f` applied to the current value. */
  def modify(f: T => T): Unit = set(f(get))

  /** Number of active subscribers. */
  def subscriberCount: Int = inner.subscriberCount

  /** A stream view (does not emit the current value on subscribe). */
  def stream: RxObservable[T] = inner.stream

  /** See [[RxVal.map]]. */
  def map[B](f: T => B): RxVal[B] = inner.map(f)

  /** See [[RxVal.flatMap]]. */
  def flatMap[B](f: T => RxVal[B]): RxVal[B] = inner.flatMap(f)

  /** See [[RxVal.flatMapRef]]. */
  def flatMapRef[B](f: T => RxRef[B]): RxVal[B] = inner.flatMapRef(f)

  /** See [[RxVal.flatMapObservable]]. */
  def flatMapObservable[B](f: T => RxObservable[B]): RxObservable[B] =
    inner.flatMapObservable(f)

  /** See [[RxVal.flatMapSubject]]. */
  def flatMapSubject[B](f: T => RxSubject[B]): RxObservable[B] =
    inner.flatMapSubject(f)

  /** See [[RxVal.zipVal]]. */
  def zipVal[U](other: RxVal[U]): RxVal[(T, U)] = inner.zipVal(other)

  /** See [[RxVal.zipRef]]. */
  def zipRef[U](other: RxRef[U]): RxVal[(T, U)] = inner.zipRef(other)

object RxRef:
  /** Creates an `RxRef` with the given initial value. */
  def apply[T](value: T): RxRef[T] = new RxRef[T](RxVal(value))
