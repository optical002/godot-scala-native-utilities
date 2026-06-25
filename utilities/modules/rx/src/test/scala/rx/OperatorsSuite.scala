package rx

import scala.collection.mutable

class OperatorsSuite extends munit.FunSuite:

  // --- map ----------------------------------------------------------------

  test("RxVal.map produces a derived cell that tracks the source") {
    val number = RxRef(5)
    val doubled = number.value.map(_ * 2)
    assertEquals(doubled.get, 10)
    number.set(10)
    assertEquals(doubled.get, 20)
  }

  test("RxRef.map works the same") {
    val name = RxRef("alice")
    val upper = name.map(_.toUpperCase)
    assertEquals(upper.get, "ALICE")
    name.set("bob")
    assertEquals(upper.get, "BOB")
  }

  test("RxObservable.map transforms emissions") {
    val dt = DisposableTracker()
    val subject = RxSubject[Int]()
    val doubled = subject.observable.map(_ * 2)
    val values = mutable.ArrayBuffer.empty[Int]
    doubled.subscribe(dt.tracker, values += _)
    subject.next(1); subject.next(2); subject.next(3)
    assertEquals(values.toList, List(2, 4, 6))
  }

  test("map can be chained") {
    val number = RxRef(2)
    val squared = number.value.map(_ * 2).map(x => x * x)
    assertEquals(squared.get, 16)
    number.set(3)
    assertEquals(squared.get, 36)
  }

  // --- stream -------------------------------------------------------------

  test("RxVal.stream does not emit the current value on subscribe") {
    val dt = DisposableTracker()
    val r = RxRef(42)
    val stream = r.value.stream
    val values = mutable.ArrayBuffer.empty[Int]
    stream.subscribe(dt.tracker, values += _)
    assertEquals(values.toList, Nil)
    r.set(100)
    assertEquals(values.toList, List(100))
  }

  test("RxRef.stream emits only on changes") {
    val dt = DisposableTracker()
    val r = RxRef("initial")
    val values = mutable.ArrayBuffer.empty[String]
    r.stream.subscribe(dt.tracker, values += _)
    r.set("changed")
    assertEquals(values.toList, List("changed"))
  }

  // --- flatMap ------------------------------------------------------------

  test("RxVal.flatMap switches to the new inner cell") {
    val outer = RxRef(1)
    val inner1 = RxRef(10)
    val inner2 = RxRef(20)
    val flat = outer.value.flatMap(x => if x == 1 then inner1.value else inner2.value)
    assertEquals(flat.get, 10)
    outer.set(2)
    assertEquals(flat.get, 20)
    outer.set(1)
    assertEquals(flat.get, 10)
  }

  test("RxVal.flatMap tracks changes of the active inner cell only") {
    val outer = RxRef(1)
    val inner1 = RxRef(10)
    val inner2 = RxRef(20)
    val flat = outer.value.flatMap(x => if x == 1 then inner1.value else inner2.value)
    assertEquals(flat.get, 10)
    inner1.set(15)
    assertEquals(flat.get, 15)
    outer.set(2)
    assertEquals(flat.get, 20)
    inner1.set(99) // inner1 no longer active
    assertEquals(flat.get, 20)
    inner2.set(25)
    assertEquals(flat.get, 25)
  }

  test("RxObservable.flatMapVal emits current value and tracks changes") {
    val dt = DisposableTracker()
    val subject = RxSubject[Int]()
    val inner = RxRef(100)
    val flat = subject.observable.flatMapVal(_ => inner.value)
    val values = mutable.ArrayBuffer.empty[Int]
    flat.subscribe(dt.tracker, values += _)

    subject.next(1) // emits current value, then re-emits via subscribe
    assertEquals(values.toList, List(100, 100))
    inner.set(200)
    assertEquals(values.toList, List(100, 100, 200))
    subject.next(2)
    assertEquals(values.toList, List(100, 100, 200, 200, 200))
  }

  test("RxVal.flatMapObservable switches observables") {
    val dt = DisposableTracker()
    val outer = RxRef(1)
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val flat = outer.value.flatMapObservable(x => if x == 1 then s1.observable else s2.observable)
    val values = mutable.ArrayBuffer.empty[Int]
    flat.subscribe(dt.tracker, values += _)

    s1.next(10)
    assertEquals(values.toList, List(10))
    outer.set(2)
    s2.next(20)
    assertEquals(values.toList, List(10, 20))
    s1.next(99) // no longer subscribed
    assertEquals(values.toList, List(10, 20))
  }

  // --- zip ----------------------------------------------------------------

  test("RxVal.zipVal combines two cells and updates on either change") {
    val name = RxRef("Alice")
    val age = RxRef(30)
    val combined = name.value.zipVal(age.value)
    assertEquals(combined.get, ("Alice", 30))
    name.set("Bob")
    assertEquals(combined.get, ("Bob", 30))
    age.set(25)
    assertEquals(combined.get, ("Bob", 25))
  }

  test("zip with different types") {
    val number = RxRef(42)
    val text = RxRef("answer")
    val combined = number.zipRef(text)
    assertEquals(combined.get, (42, "answer"))
    number.set(100)
    assertEquals(combined.get, (100, "answer"))
  }

  test("multiple zips compose") {
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

  // --- join ---------------------------------------------------------------

  test("RxObservable.joinObservable merges both sources in order") {
    val dt = DisposableTracker()
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val joined = s1.observable.joinObservable(s2.observable)
    val values = mutable.ArrayBuffer.empty[Int]
    joined.subscribe(dt.tracker, values += _)
    s1.next(1); s2.next(2); s1.next(3); s2.next(4)
    assertEquals(values.toList, List(1, 2, 3, 4))
  }

  test("joins can be chained across three subjects") {
    val dt = DisposableTracker()
    val s1 = RxSubject[Int]()
    val s2 = RxSubject[Int]()
    val s3 = RxSubject[Int]()
    val joined = s1.joinSubject(s2).joinSubject(s3)
    val values = mutable.ArrayBuffer.empty[Int]
    joined.subscribe(dt.tracker, values += _)
    s1.next(1); s2.next(2); s3.next(3); s1.next(4)
    assertEquals(values.toList, List(1, 2, 3, 4))
  }

  // --- toVal --------------------------------------------------------------

  test("RxSubject.toVal seeds and updates from emissions") {
    val dt = DisposableTracker()
    val subject = RxSubject[Int]()
    val v = subject.toVal(0, dt.tracker)
    assertEquals(v.get, 0)
    subject.next(42)
    assertEquals(v.get, 42)
  }
