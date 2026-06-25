package rx

import scala.collection.mutable
import java.lang.ref.WeakReference

/** A read-only reactive cell holding a current value.
  *
  * On `subscribe`, the subscriber is invoked immediately with the current value,
  * then again whenever the value changes. Updates are deduplicated: subscribers
  * are only notified when the value actually changes (by `==`).
  *
  * Obtain an `RxVal` from an [[RxRef]] via `.value`, or derive one with the
  * operators below.
  *
  * Port note (vs. the Rust crate): Rust stored subscribers as
  * `Rc<RefCell<Box<dyn FnMut>>>` and used `Weak` upgrades plus a `_lifetime_tracker`
  * field to (a) avoid keeping derived cells alive and (b) keep the source
  * subscription alive for as long as the derived cell. On Scala Native the GC owns
  * lifetimes, so a derived cell keeps its source subscription alive by simply
  * holding the source `DisposableTracker` in a field. To avoid the source keeping
  * the derived cell alive (mirroring Rust's `Weak`), the bridging subscription
  * references the derived cell through a [[java.lang.ref.WeakReference]]; once the
  * derived cell is collected the bridge becomes a no-op and is pruned, which is
  * what lets `subscriberCount` drop back to 0 after a derived cell goes away.
  */
final class RxVal[T] private[rx] (initial: T):
  private var current: T = initial
  private val subscribers = mutable.ArrayBuffer.empty[T => Unit]

  // Keeps source subscriptions of derived cells (map/flatMap/zip/...) alive for
  // as long as this cell is reachable. Never read; reachability is the point.
  private[rx] var lifetime: AnyRef = null

  /** The current value. */
  def get: T = current

  /** Subscribes to value changes. The subscriber is called immediately with the
    * current value, then on every change, until `tracker` is disposed.
    */
  def subscribe(tracker: Tracker, f: T => Unit): Unit =
    f(current)
    subscribers += f
    tracker.add(() => subscribers -= f)

  /** Number of active subscribers. */
  def subscriberCount: Int = subscribers.length

  /** Internal: update the value and notify subscribers if it changed. */
  private[rx] def update(value: T): Unit =
    if current != value then
      current = value
      // Snapshot so a subscriber adding/removing during notification is safe.
      subscribers.toArray.foreach(_(value))

  /** A stream view that does NOT emit the current value on subscribe and only
    * emits subsequent changes.
    */
  def stream: RxObservable[T] =
    val subject = RxSubject[T]()
    val tracker = DisposableTracker()
    var first = true
    subscribe(
      tracker.tracker,
      value =>
        if first then first = false
        else subject.next(value)
    )
    val obs = subject.observable
    obs.lifetime = tracker
    obs

  /** Maps this cell with `f`, producing a derived cell that always holds the
    * transformed value and updates (deduplicated) when this cell changes.
    */
  def map[B](f: T => B): RxVal[B] =
    val tracker = DisposableTracker()
    val result = new RxVal[B](f(current))
    val weak = new WeakReference(result)
    subscribe(
      tracker.tracker,
      value =>
        val r = weak.get()
        if r != null then r.update(f(value))
        else tracker.dispose() // derived cell collected: drop source subscription
    )
    result.lifetime = tracker
    result

  /** Flat-maps this cell with `f`. The derived cell reflects the current value of
    * the inner cell produced by `f`, switching whenever this cell changes and
    * tracking changes of the active inner cell.
    */
  def flatMap[B](f: T => RxVal[B]): RxVal[B] =
    val initialInner = f(current)
    val result = new RxVal[B](initialInner.get)
    val weak = new WeakReference(result)

    val outerTracker = DisposableTracker()
    // Reassigned each time the outer switches; disposing cancels the old inner sub.
    var innerTracker = DisposableTracker()
    // Keeps the active inner cell alive while it is the source.
    var currentInner: RxVal[B] = initialInner

    def subscribeInner(inner: RxVal[B]): Unit =
      inner.subscribe(
        innerTracker.tracker,
        v =>
          val r = weak.get()
          if r != null then r.update(v)
      )

    subscribeInner(initialInner)

    var lastOuter: T = current
    subscribe(
      outerTracker.tracker,
      outer =>
        val r = weak.get()
        if r == null then outerTracker.dispose()
        else if lastOuter != outer then
          lastOuter = outer
          val newInner = f(outer)
          innerTracker.dispose()
          innerTracker = DisposableTracker()
          r.update(newInner.get)
          subscribeInner(newInner)
          currentInner = newInner
    )

    // Keep outer subscription + active inner cell reachable via the result.
    result.lifetime = (outerTracker, () => currentInner)
    result

  /** Flat-maps with a function returning an [[RxRef]]. */
  def flatMapRef[B](f: T => RxRef[B]): RxVal[B] =
    flatMap(x => f(x).value)

  /** Flat-maps with a function returning an [[RxObservable]], switching to the new
    * observable on each change.
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

  /** Combines this cell with `other` into a cell of tuples that updates
    * (deduplicated) whenever either source changes.
    */
  def zipVal[U](other: RxVal[U]): RxVal[(T, U)] =
    val result = new RxVal[(T, U)]((current, other.get))
    val weak = new WeakReference(result)
    val tracker1 = DisposableTracker()
    val tracker2 = DisposableTracker()
    subscribe(
      tracker1.tracker,
      self =>
        val r = weak.get()
        if r != null then r.update((self, other.get))
    )
    other.subscribe(
      tracker2.tracker,
      o =>
        val r = weak.get()
        if r != null then r.update((current, o))
    )
    result.lifetime = (tracker1, tracker2)
    result

  /** Combines this cell with an [[RxRef]]. */
  def zipRef[U](other: RxRef[U]): RxVal[(T, U)] =
    zipVal(other.value)

object RxVal:
  /** Internal constructor; users create an [[RxRef]] and call `.value`. */
  private[rx] def apply[T](value: T): RxVal[T] = new RxVal[T](value)
