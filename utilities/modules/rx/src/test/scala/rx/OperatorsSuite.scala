package rx

import scala.collection.mutable
import cats.syntax.all.*

class OperatorsSuite extends munit.FunSuite:

  test("RxVal.map produces a derived cell that tracks the source") {
    given Tracker = DisposableTracker()
    val number = RxRef(5)
    val doubled = number.map(_ * 2)
    assertEquals(doubled.get, 10)
    number.set(10)
    assertEquals(doubled.get, 20)
  }

  test("RxRef.map via cats delegates to the inner cell") {
    given Tracker = DisposableTracker()
    val name = RxRef("alice")
    val upper = name.map(_.toUpperCase)
    assertEquals(upper.get, "ALICE")
    name.set("bob")
    assertEquals(upper.get, "BOB")
  }

  test("RxObservable.map transforms emissions") {
    given Tracker = DisposableTracker()
    val subject = RxSubject[Int]()
    val doubled = subject.observable.map(_ * 2)
    val values = mutable.ArrayBuffer.empty[Int]
    doubled.subscribe(values += _)
    subject.next(1); subject.next(2); subject.next(3)
    assertEquals(values.toList, List(2, 4, 6))
  }

  test("map can be chained") {
    given Tracker = DisposableTracker()
    val number = RxRef(2)
    val squared = number.map(_ * 2).map(x => x * x)
    assertEquals(squared.get, 16)
    number.set(3)
    assertEquals(squared.get, 36)
  }

  test("RxVal.stream does not emit the current value on subscribe") {
    given Tracker = DisposableTracker()
    val r = RxRef(42)
    val stream = r.stream
    val values = mutable.ArrayBuffer.empty[Int]
    stream.subscribe(values += _)
    assertEquals(values.toList, Nil)
    r.set(100)
    assertEquals(values.toList, List(100))
  }

  test("RxRef.stream emits only on changes") {
    given Tracker = DisposableTracker()
    val r = RxRef("initial")
    val values = mutable.ArrayBuffer.empty[String]
    r.stream.subscribe(values += _)
    r.set("changed")
    assertEquals(values.toList, List("changed"))
  }

  test("RxVal.flatMap switches to the new inner cell") {
    given Tracker = DisposableTracker()
    val outer = RxRef(1)
    val inner1 = RxRef(10)
    val inner2 = RxRef(20)
    val flat = outer.flatMap(x => if x == 1 then inner1 else inner2)
    assertEquals(flat.get, 10)
    outer.set(2)
    assertEquals(flat.get, 20)
    outer.set(1)
    assertEquals(flat.get, 10)
  }

  test("RxVal.flatMap tracks changes of the active inner cell only") {
    given Tracker = DisposableTracker()
    val outer = RxRef(1)
    val inner1 = RxRef(10)
    val inner2 = RxRef(20)
    val flat = outer.flatMap(x => if x == 1 then inner1 else inner2)
    assertEquals(flat.get, 10)
    inner1.set(15)
    assertEquals(flat.get, 15)
    outer.set(2)
    assertEquals(flat.get, 20)
    inner1.set(99)
    assertEquals(flat.get, 20)
    inner2.set(25)
    assertEquals(flat.get, 25)
  }

  test("RxObservable.flatMapVal emits current value and tracks changes") {
    given Tracker = DisposableTracker()
    val subject = RxSubject[Int]()
    val inner = RxRef(100)
    val flat = subject.observable.flatMap(_ => inner.value)
    val values = mutable.ArrayBuffer.empty[Int]
    flat.subscribe(values += _)

    subject.next(1)
    assertEquals(values.toList, List(100, 100))
    inner.set(200)
    assertEquals(values.toList, List(100, 100, 200))
    subject.next(2)
    assertEquals(values.toList, List(100, 100, 200, 200, 200))
  }

  test("RxVal.flatMapObservable switches observables") {
    given Tracker = DisposableTracker()
    val outer = RxRef(1)
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val flat = outer.flatMap(x => if x == 1 then s1.observable else s2.observable)
    val values = mutable.ArrayBuffer.empty[Int]
    flat.subscribe(values += _)

    s1.next(10)
    assertEquals(values.toList, List(10))
    outer.set(2)
    s2.next(20)
    assertEquals(values.toList, List(10, 20))
    s1.next(99)
    assertEquals(values.toList, List(10, 20))
  }

  test("RxVal.zipVal combines two cells and updates on either change") {
    given Tracker = DisposableTracker()
    val name = RxRef("Alice")
    val age = RxRef(30)
    val combined = name.zipVal(age.value)
    assertEquals(combined.get, ("Alice", 30))
    name.set("Bob")
    assertEquals(combined.get, ("Bob", 30))
    age.set(25)
    assertEquals(combined.get, ("Bob", 25))
  }

  test("zip with different types") {
    given Tracker = DisposableTracker()
    val number = RxRef(42)
    val text = RxRef("answer")
    val combined = number.zipRef(text)
    assertEquals(combined.get, (42, "answer"))
    number.set(100)
    assertEquals(combined.get, (100, "answer"))
  }

  test("multiple zips compose") {
    given Tracker = DisposableTracker()
    val a = RxRef(1)
    val b = RxRef(2)
    val c = RxRef(3)
    val abc = a.zipVal(b.value).map((x, y) => (x, y, c.get))
    assertEquals(abc.get, (1, 2, 3))
    a.set(10)
    assertEquals(abc.get, (10, 2, 3))
    b.set(20)
    assertEquals(abc.get, (10, 20, 3))
  }

  test("cats tupled/mapN on cells is emit-on-either") {
    given Tracker = DisposableTracker()
    val a = RxRef(1)
    val b = RxRef(2)
    val tupled = (a.value, b.value).tupled
    assertEquals(tupled.get, (1, 2))
    a.set(10)
    assertEquals(tupled.get, (10, 2))
    b.set(20)
    assertEquals(tupled.get, (10, 20))

    val summed = (a.value, b.value).mapN(_ + _)
    assertEquals(summed.get, 30)
    a.set(100)
    assertEquals(summed.get, 120)
  }

  test("RxObservable.joinObservable merges both sources in order") {
    given Tracker = DisposableTracker()
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val joined = s1.observable.join(s2.observable)
    val values = mutable.ArrayBuffer.empty[Int]
    joined.subscribe(values += _)
    s1.next(1); s2.next(2); s1.next(3); s2.next(4)
    assertEquals(values.toList, List(1, 2, 3, 4))
  }

  test("joins can be chained across three subjects") {
    given Tracker = DisposableTracker()
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val s3 = RxSubject[Int]()
    val joined = s1.join(s2).join(s3)
    val values = mutable.ArrayBuffer.empty[Int]
    joined.subscribe(values += _)
    s1.next(1); s2.next(2); s3.next(3); s1.next(4)
    assertEquals(values.toList, List(1, 2, 3, 4))
  }

  test("RxSubject.toVal seeds and updates from emissions") {
    given Tracker = DisposableTracker()
    val subject = RxSubject[Int]()
    val v = subject.toVal(0)
    assertEquals(v.get, 0)
    subject.next(42)
    assertEquals(v.get, 42)
  }
