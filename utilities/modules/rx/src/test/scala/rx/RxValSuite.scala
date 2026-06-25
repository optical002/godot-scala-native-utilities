package rx

import scala.collection.mutable

class RxValSuite extends munit.FunSuite:

  test("basic get/set") {
    val r = RxRef(42)
    assertEquals(r.get, 42)
    r.set(100)
    assertEquals(r.get, 100)
  }

  test("subscribe is called immediately with the current value") {
    val dt = DisposableTracker()
    val r = RxRef(42)
    var called = false
    r.value.subscribe(dt.tracker, _ => called = true)
    assert(called)
  }

  test("subscriber receives initial value and all updates") {
    val dt = DisposableTracker()
    val r = RxRef(0)
    val values = mutable.ArrayBuffer.empty[Int]
    r.value.subscribe(dt.tracker, values += _)
    r.set(1); r.set(2); r.set(3)
    assertEquals(values.toList, List(0, 1, 2, 3))
  }

  test("multiple subscribers all notified") {
    val dt = DisposableTracker()
    val r = RxRef(0)
    var c1 = 0
    var c2 = 0
    r.value.subscribe(dt.tracker, _ => c1 += 1)
    r.value.subscribe(dt.tracker, _ => c2 += 1)
    assertEquals(c1, 1)
    assertEquals(c2, 1)
    r.set(42)
    assertEquals(c1, 2)
    assertEquals(c2, 2)
  }

  test("setting the same value does not trigger (dedup)") {
    val dt = DisposableTracker()
    val r = RxRef(42)
    var count = 0
    r.value.subscribe(dt.tracker, _ => count += 1)
    assertEquals(count, 1)
    r.set(42)
    assertEquals(count, 1)
    r.set(42)
    assertEquals(count, 1)
    r.set(100)
    assertEquals(count, 2)
    r.set(100)
    assertEquals(count, 2)
  }

  test("Option values dedup and update correctly") {
    val dt = DisposableTracker()
    val r = RxRef(Option.empty[Int])
    val values = mutable.ArrayBuffer.empty[Option[Int]]
    r.value.subscribe(dt.tracker, values += _)
    r.set(Some(42)); r.set(None); r.set(Some(100))
    assertEquals(values.toList, List(None, Some(42), None, Some(100)))
  }

  test("modify triggers subscribers") {
    val dt = DisposableTracker()
    val r = RxRef(0)
    val values = mutable.ArrayBuffer.empty[Int]
    r.value.subscribe(dt.tracker, values += _)
    r.modify(_ + 10)
    r.modify(_ * 2)
    assertEquals(values.toList, List(0, 10, 20))
  }
