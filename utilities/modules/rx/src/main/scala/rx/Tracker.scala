package rx

import scala.collection.mutable

/** Tracks reactive subscriptions and cleans them up when disposed.
  *
  * A `Tracker` is the lifetime handle that every subscription requires. When the
  * owning [[DisposableTracker]] is disposed, all subscriptions registered through
  * the tracker are removed.
  *
  * A `Tracker` cannot be created directly: build a [[DisposableTracker]] and call
  * `.tracker`.
  *
  * Port note (vs. the Rust crate): Rust used `Rc<RefCell<Vec<Box<dyn FnOnce()>>>>`
  * with a separate `Rc<()>` owner count plus a `Drop` impl to fire cleanups when
  * the last `DisposableTracker` clone was dropped. On Scala Native we have a GC, so
  * lifetimes are managed by explicit `dispose()` instead of `Drop`. The cleanup
  * list is a plain mutable buffer shared by reference.
  */
final class Tracker private[rx] (private[rx] val cleanups: mutable.ArrayBuffer[() => Unit]):

  /** Registers a cleanup to run when the owning tracker is disposed. Internal:
    * used by the reactive primitives to deregister their subscriptions.
    */
  private[rx] def add(cleanup: () => Unit): Unit =
    cleanups += cleanup

  /** Number of active subscriptions tracked. */
  def subscriptionCount: Int = cleanups.length

  /** Ties another [[DisposableTracker]]'s lifetime to this one: when this
    * tracker's parent is disposed, `child` is disposed too. Useful for
    * hierarchical cleanup.
    */
  def track(child: DisposableTracker): Unit =
    add(() => child.dispose())

/** A tracker that can be manually disposed.
  *
  * Unlike [[Tracker]], `DisposableTracker` exposes `dispose()` to explicitly clean
  * up all subscriptions. After disposal it stays valid and can track new
  * subscriptions again.
  */
final class DisposableTracker:
  private val _tracker = new Tracker(mutable.ArrayBuffer.empty)

  /** The underlying [[Tracker]] to pass to `subscribe` methods. */
  def tracker: Tracker = _tracker

  /** Disposes all currently-tracked subscriptions. Idempotent; the tracker can
    * be reused afterwards.
    */
  def dispose(): Unit =
    if _tracker.cleanups.nonEmpty then
      val pending = _tracker.cleanups.toArray
      _tracker.cleanups.clear()
      pending.foreach(_())

  /** Number of active subscriptions tracked. */
  def subscriptionCount: Int = _tracker.subscriptionCount

object DisposableTracker:
  def apply(): DisposableTracker = new DisposableTracker
