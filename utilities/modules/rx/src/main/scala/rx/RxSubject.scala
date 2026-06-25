package rx

trait RxSubject[T]:
  def next(value: T): Unit
  def observable: RxObservable[T]
  def subscriberCount: Int
  def toVal(initial: T)(using Tracker): RxVal[T]
  def flatMap[S[_], B](f: T => S[B])(using AsObservable[S], Tracker): RxObservable[B]
  def join[S[_]](other: S[T])(using AsObservable[S], Tracker): RxObservable[T]

object RxSubject:

  def apply[T](): RxSubject[T] = new Impl[T](RxObservable[T]())

  private final class Impl[T](inner: RxObservable[T]) extends RxSubject[T]:
    def next(value: T): Unit = inner.emit(value)
    def observable: RxObservable[T] = inner
    def subscriberCount: Int = inner.subscriberCount
    def toVal(initial: T)(using Tracker): RxVal[T] = inner.toVal(initial)
    def flatMap[S[_], B](f: T => S[B])(using AsObservable[S], Tracker): RxObservable[B] =
      inner.flatMap(f)
    def join[S[_]](other: S[T])(using AsObservable[S], Tracker): RxObservable[T] =
      inner.join(other)
