package rx

import scala.collection.mutable
import cats.{FlatMap, Apply}

trait RxVal[T]:
  def get: T
  def subscribe(f: T => Unit)(using Tracker): Unit
  def subscriberCount: Int
  def stream(using Tracker): RxObservable[T]
  def flatMap[S[_], B](f: T => S[B])(using sw: Switchable[S], t: Tracker): sw.Out[B]
  def zipVal[U](other: RxVal[U])(using Tracker): RxVal[(T, U)]
  def zipRef[U](other: RxRef[U])(using Tracker): RxVal[(T, U)]
  private[rx] def update(value: T): Unit

object RxVal:

  private[rx] def apply[T](value: T): RxVal[T] = new Impl[T](value)
  def const[T](value: T): RxVal[T] = new Impl[T](value)

  private final class Impl[T](initial: T) extends RxVal[T]:
    private var current: T = initial
    private val subscribers = mutable.ArrayBuffer.empty[T => Unit]

    def get: T = current
    def subscribe(f: T => Unit)(using tracker: Tracker): Unit =
      f(current)
      subscribers += f
      tracker.add(() => subscribers -= f)

    def subscriberCount: Int = subscribers.length

    private[rx] def update(value: T): Unit =
      if current != value then
        current = value
        subscribers.toArray.foreach(_(value))

    def stream(using Tracker): RxObservable[T] =
      val subject = RxSubject[T]()
      var first = true
      subscribe { value =>
        if first then first = false
        else subject.next(value)
      }
      subject.observable

    def flatMap[S[_], B](f: T => S[B])(using sw: Switchable[S], t: Tracker): sw.Out[B] =
      sw.switch(this, f)

    def zipVal[U](other: RxVal[U])(using Tracker): RxVal[(T, U)] =
      val result = RxVal[(T, U)]((current, other.get))
      subscribe(self => result.update((self, other.get)))
      other.subscribe(o => result.update((current, o)))
      result

    def zipRef[U](other: RxRef[U])(using Tracker): RxVal[(T, U)] =
      zipVal(other.value)

  // Operators sourced from cats. Summoning any of these captures the in-scope
  // Tracker; every derived cell anchors its bridge subscription on that tracker,
  // so disposing it tears the derived graph down. NOTE: RxVal is a stateful,
  // switching cell — it is intentionally NOT a lawful Monad (no pure/tailRecM
  // that honours the laws over future updates), so only Functor/FlatMap/Apply
  // are provided.

  given rxValInstance(using outer: Tracker): (Apply[RxVal] & FlatMap[RxVal]) =
    new Apply[RxVal] with FlatMap[RxVal]:
      def map[A, B](fa: RxVal[A])(f: A => B): RxVal[B] =
        val result = RxVal[B](f(fa.get))
        fa.subscribe(a => result.update(f(a)))
        result

      // emit-on-either: distinct from the flatMap-derived product, which would
      // be switch-on-change. This makes cats `tupled`/`mapN` correct for cells.
      override def product[A, B](fa: RxVal[A], fb: RxVal[B]): RxVal[(A, B)] =
        val result = RxVal[(A, B)]((fa.get, fb.get))
        fa.subscribe(a => result.update((a, fb.get)))
        fb.subscribe(b => result.update((fa.get, b)))
        result

      def flatMap[A, B](fa: RxVal[A])(f: A => RxVal[B]): RxVal[B] =
        fa.flatMap(f)

      // Pragmatic, NOT lawful for the reactive dimension: loops on current
      // values only. RxVal is not advertised as a Monad, so this only backs the
      // FlatMap interface's required method.
      def tailRecM[A, B](a: A)(f: A => RxVal[Either[A, B]]): RxVal[B] =
        @annotation.tailrec
        def step(cur: A): B =
          f(cur).get match
            case Right(b) => b
            case Left(a2) => step(a2)
        flatMap(f(a)) {
          case Right(b) => RxVal.const(b)
          case Left(a2) => RxVal.const(step(a2))
        }
