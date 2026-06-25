package rx

import scala.collection.mutable

trait Tracker:
  def subscriptionCount: Int
  def track(child: DisposableTracker): Unit
  private[rx] def add(cleanup: () => Unit): Unit

trait DisposableTracker extends Tracker:
  def dispose(): Unit

object Tracker:
  def apply(): Tracker = DisposableTracker()

object DisposableTracker:
  def apply(): DisposableTracker = new Impl

  private final class Impl extends DisposableTracker:
    private val cleanups = mutable.ArrayBuffer.empty[() => Unit]

    private[rx] def add(cleanup: () => Unit): Unit =
      cleanups += cleanup

    def subscriptionCount: Int = cleanups.length

    def track(child: DisposableTracker): Unit =
      add(() => child.dispose())

    def dispose(): Unit =
      if cleanups.nonEmpty then
        val pending = cleanups.toArray
        cleanups.clear()
        pending.foreach(_())
