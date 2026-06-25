package rx

import scala.collection.mutable

/** A read-only stream of events.
  *
  * Unlike [[RxVal]], an `RxObservable` holds no current value: subscribers are
  * only called on emissions after they subscribe, never immediately. Useful for
  * discrete events (clicks, network messages, ...).
  *
  * Obtain one from an [[RxSubject]] via `.observable`, or derive one with the
  * operators below.
  */
final class RxObservable[T] private[rx] ():
  private val subscribers = mutable.ArrayBuffer.empty[T => Unit]

  // Keeps source subscriptions of derived observables alive while this one is
  // reachable. See the port note on RxVal. Never read.
  private[rx] var lifetime: AnyRef = null

  /** Subscribes to events. Called on each emission until `tracker` is disposed;
    * NOT called immediately on subscribe.
    */
  def subscribe(tracker: Tracker, f: T => Unit): Unit =
    subscribers += f
    tracker.add(() => subscribers -= f)

  /** Number of active subscribers. */
  def subscriberCount: Int = subscribers.length

  /** Internal: emit an event to all current subscribers. */
  private[rx] def emit(value: T): Unit =
    // Snapshot so subscribers added/removed during notification are safe.
    subscribers.toArray.foreach(_(value))

  /** Converts to an [[RxVal]] seeded with `initial`, updated on each emission for
    * as long as `tracker` lives.
    */
  def toVal(initial: T, tracker: Tracker): RxVal[T] =
    val ref = RxRef(initial)
    subscribe(tracker, value => ref.set(value))
    ref.value

  /** Maps emissions with `f`. */
  def map[B](f: T => B): RxObservable[B] =
    val subject = RxSubject[B]()
    val tracker = DisposableTracker()
    subscribe(tracker.tracker, value => subject.next(f(value)))
    val obs = subject.observable
    obs.lifetime = tracker
    obs

  /** Flat-maps with a function returning an [[RxVal]]. On each emission it emits
    * the inner cell's current value AND subscribes to its future changes
    * (cancelling the previous inner subscription).
    */
  def flatMapVal[B](f: T => RxVal[B]): RxObservable[B] =
    val subject = RxSubject[B]()
    val outerTracker = DisposableTracker()
    var innerTracker = DisposableTracker()
    subscribe(
      outerTracker.tracker,
      outer =>
        val newInner = f(outer)
        innerTracker.dispose()
        innerTracker = DisposableTracker()
        subject.next(newInner.get)
        newInner.subscribe(innerTracker.tracker, v => subject.next(v))
    )
    val obs = subject.observable
    obs.lifetime = (outerTracker, () => innerTracker)
    obs

  /** Flat-maps with a function returning an [[RxRef]]. */
  def flatMapRef[B](f: T => RxRef[B]): RxObservable[B] =
    flatMapVal(x => f(x).value)

  /** Flat-maps with a function returning an [[RxObservable]], switching to the new
    * observable on each emission.
    */
  def flatMapObservable[B](f: T => RxObservable[B]): RxObservable[B] =
    val subject = RxSubject[B]()
    val outerTracker = DisposableTracker()
    var innerTracker = DisposableTracker()
    subscribe(
      outerTracker.tracker,
      outer =>
        val newInner = f(outer)
        innerTracker.dispose()
        innerTracker = DisposableTracker()
        newInner.subscribe(innerTracker.tracker, v => subject.next(v))
    )
    val obs = subject.observable
    obs.lifetime = (outerTracker, () => innerTracker)
    obs

  /** Flat-maps with a function returning an [[RxSubject]]. */
  def flatMapSubject[B](f: T => RxSubject[B]): RxObservable[B] =
    flatMapObservable(x => f(x).observable)

  /** Joins this observable with `other`; the result emits whenever either
    * source emits.
    */
  def joinObservable(other: RxObservable[T]): RxObservable[T] =
    val subject = RxSubject[T]()
    val tracker1 = DisposableTracker()
    val tracker2 = DisposableTracker()
    subscribe(tracker1.tracker, v => subject.next(v))
    other.subscribe(tracker2.tracker, v => subject.next(v))
    val obs = subject.observable
    obs.lifetime = (tracker1, tracker2)
    obs

  /** Joins this observable with an [[RxSubject]]. */
  def joinSubject(other: RxSubject[T]): RxObservable[T] =
    joinObservable(other.observable)

object RxObservable:
  /** Internal constructor; users create an [[RxSubject]] and call `.observable`. */
  private[rx] def apply[T](): RxObservable[T] = new RxObservable[T]()
