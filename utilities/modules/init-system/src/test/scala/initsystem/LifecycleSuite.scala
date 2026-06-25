package initsystem

class LifecycleSuite extends munit.FunSuite:

  private def setup(): (InitContext, ApiCalls) =
    val calls = ApiCalls()
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    ApiTestBacking(calls).init(())
    ctx.processPending()
    (ctx, calls)

  test("process is called with the correct delta") {
    val (ctx, calls) = setup()
    ctx.forEach(_.process(0.016))
    assertEquals(calls.process, 1)
    assert(math.abs(calls.lastProcessDelta - 0.016) < 1e-12)
  }

  test("physicsProcess is called with the correct delta") {
    val (ctx, calls) = setup()
    ctx.forEach(_.physicsProcess(0.032))
    assertEquals(calls.physicsProcess, 1)
    assert(math.abs(calls.lastPhysicsDelta - 0.032) < 1e-12)
  }

  test("enterTree is called on init") {
    val (ctx, calls) = setup()
    ctx.forEach(_.enterTree())
    assertEquals(calls.enterTree, 1)
  }

  test("exitTree is called on init") {
    val (ctx, calls) = setup()
    ctx.forEach(_.exitTree())
    assertEquals(calls.exitTree, 1)
  }

  test("onFree is called when an init is removed") {
    val calls = ApiCalls()
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = ApiTestBacking(calls).init(())
    ctx.processPending()
    init.free()
    ctx.processPending()
    assertEquals(calls.onFree, 1)
  }
