package rx

import scala.collection.mutable
import cats.{FlatMap, Apply}

trait RxObservable[T]:
  def subscribe(f: T => Unit)(using Tracker): Unit
  def subscriberCount: Int
  def toVal(initial: T)(using Tracker): RxVal[T]
  def flatMap[S[_], B](f: T => S[B])(using AsObservable[S], Tracker): RxObservable[B]
  def join[S[_]](other: S[T])(using AsObservable[S], Tracker): RxObservable[T]
  private[rx] def emit(value: T): Unit

object RxObservable:

  private[rx] def apply[T](): RxObservable[T] = new Impl[T]

  private final class Impl[T] extends RxObservable[T]:
    private val subscribers = mutable.ArrayBuffer.empty[T => Unit]

    def subscribe(f: T => Unit)(using tracker: Tracker): Unit =
      subscribers += f
      tracker.add(() => subscribers -= f)

    def subscriberCount: Int = subscribers.length

    private[rx] def emit(value: T): Unit =
      subscribers.toArray.foreach(_(value))

    def toVal(initial: T)(using Tracker): RxVal[T] =
      val ref = RxRef(initial)
      subscribe(value => ref.set(value))
      ref.value

    def flatMap[S[_], B](f: T => S[B])(using src: AsObservable[S], outer: Tracker): RxObservable[B] =
      val subject = RxSubject[B]()
      var inner = freshInner
      subscribe { o =>
        val newInner = f(o)
        inner.dispose()
        inner = freshInner
        src.current(newInner).foreach(subject.next)
        src.subscribeTo(newInner, v => subject.next(v))(using inner)
      }
      subject.observable

    def join[S[_]](other: S[T])(using src: AsObservable[S], t: Tracker): RxObservable[T] =
      val subject = RxSubject[T]()
      subscribe(v => subject.next(v))
      src.current(other).foreach(subject.next)
      src.subscribeTo(other, v => subject.next(v))
      subject.observable

    // A swappable inner tracker tied to the captured outer Tracker, so switching
    // disposes the previous inner subscription and the whole graph tears down
    // with the outer tracker.
    private def freshInner(using outer: Tracker): DisposableTracker =
      val t = DisposableTracker()
      outer.track(t)
      t

  // map/flatMap from cats. No `pure` (a stream has no current value), so no
  // Monad — only Functor (via Apply) + FlatMap. flatMap is switch-on-change,
  // matching flatMapObservable.

  given rxObservableInstance(using outer: Tracker): (Apply[RxObservable] & FlatMap[RxObservable]) =
    new Apply[RxObservable] with FlatMap[RxObservable]:
      def map[A, B](fa: RxObservable[A])(f: A => B): RxObservable[B] =
        val subject = RxSubject[B]()
        fa.subscribe(v => subject.next(f(v)))
        subject.observable

      def flatMap[A, B](fa: RxObservable[A])(f: A => RxObservable[B]): RxObservable[B] =
        fa.flatMap(f)

      // Degenerate but total: streams have no current value, so each step only
      // re-subscribes on Left. Not advertised as a Monad.
      def tailRecM[A, B](a: A)(f: A => RxObservable[Either[A, B]]): RxObservable[B] =
        val subject = RxSubject[B]()
        def drive(in: RxObservable[Either[A, B]]): Unit =
          in.subscribe {
            case Right(b) => subject.next(b)
            case Left(a2) => drive(f(a2))
          }
        drive(f(a))
        subject.observable
