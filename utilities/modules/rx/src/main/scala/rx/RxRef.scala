package rx

import cats.syntax.all.*

trait RxRef[T]:
  def set(value: T): Unit
  def get: T
  def value: RxVal[T]
  def modify(f: T => T): Unit
  def subscribe(f: T => Unit)(using Tracker): Unit
  def subscriberCount: Int
  def stream(using Tracker): RxObservable[T]
  def map[B](f: T => B)(using Tracker): RxVal[B]
  def flatMap[S[_], B](f: T => S[B])(using sw: Switchable[S], t: Tracker): sw.Out[B]
  def zipVal[U](other: RxVal[U])(using Tracker): RxVal[(T, U)]
  def zipRef[U](other: RxRef[U])(using Tracker): RxVal[(T, U)]

object RxRef:

  def apply[T](value: T): RxRef[T] = new Impl[T](RxVal(value))

  private final class Impl[T](inner: RxVal[T]) extends RxRef[T]:
    def set(value: T): Unit = inner.update(value)
    def get: T = inner.get
    def value: RxVal[T] = inner
    def modify(f: T => T): Unit = set(f(get))
    def subscribe(f: T => Unit)(using Tracker): Unit = inner.subscribe(f)
    def subscriberCount: Int = inner.subscriberCount
    def stream(using Tracker): RxObservable[T] = inner.stream
    def map[B](f: T => B)(using Tracker): RxVal[B] = inner.map(f)
    def flatMap[S[_], B](f: T => S[B])(using sw: Switchable[S], t: Tracker): sw.Out[B] =
      inner.flatMap(f)
    def zipVal[U](other: RxVal[U])(using Tracker): RxVal[(T, U)] =
      inner.zipVal(other)
    def zipRef[U](other: RxRef[U])(using Tracker): RxVal[(T, U)] =
      inner.zipRef(other)
