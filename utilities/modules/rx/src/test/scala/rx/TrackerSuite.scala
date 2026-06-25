package rx

class TrackerSuite extends munit.FunSuite:

  test("disposing a tracker removes its subscription") {
    val r = RxRef(0)
    var count = 0
    val dt = DisposableTracker()
    r.subscribe(_ => count += 1)(using dt)
    assertEquals(count, 1)
    r.set(1)
    assertEquals(count, 2)
    dt.dispose()
    r.set(2)
    assertEquals(count, 2)
  }

  test("multiple trackers dispose independently") {
    val r = RxRef(0)
    var c1 = 0
    var c2 = 0
    val t1 = DisposableTracker()
    r.subscribe(_ => c1 += 1)(using t1)

    val t2 = DisposableTracker()
    r.subscribe(_ => c2 += 1)(using t2)
    r.set(1)
    assertEquals(c1, 2)
    assertEquals(c2, 2)

    t2.dispose()
    r.set(2)
    assertEquals(c1, 3)
    assertEquals(c2, 2)

    t1.dispose()
    r.set(3)
    assertEquals(c1, 3)
    assertEquals(c2, 2)
  }

  test("resubscribe after dispose with the same tracker") {
    val dt = DisposableTracker()
    val r = RxRef(0)
    var count = 0
    r.subscribe(_ => count += 1)(using dt)
    r.set(1)
    assertEquals(count, 2)

    dt.dispose()
    r.set(2)
    assertEquals(count, 2)

    r.subscribe(_ => count += 1)(using dt)
    assertEquals(count, 3)
    r.set(3)
    assertEquals(count, 4)
  }

  test("track ties a child tracker's lifetime to the parent") {
    val r = RxRef(0)
    var count = 0
    val parent = DisposableTracker()
    val child = DisposableTracker()
    r.subscribe(_ => count += 1)(using child)
    parent.track(child)

    assertEquals(count, 1)
    r.set(1)
    assertEquals(count, 2)

    parent.dispose()
    r.set(2)
    assertEquals(count, 2)
  }

  test("subscriptionCount reflects active subscriptions") {
    val r = RxRef(0)
    val dt = DisposableTracker()
    assertEquals(dt.subscriptionCount, 0)
    r.subscribe(_ => ())(using dt)
    r.subscribe(_ => ())(using dt)
    assertEquals(dt.subscriptionCount, 2)
    dt.dispose()
    assertEquals(dt.subscriptionCount, 0)
  }
