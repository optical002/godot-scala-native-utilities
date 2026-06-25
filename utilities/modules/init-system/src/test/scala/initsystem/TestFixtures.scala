package initsystem

// Engine-independent test fixtures shared across the suites in this package.
// These factories carry their values directly and take no init params, so their
// Params type is Unit; the ParentId and InitContext arrive implicitly in initInner.

final class MyInit(val selfId: InitId, val ctx: InitContext, val value: Double, val backingValue: Double)
    extends InitBase

final class MyBacking(value: Double, backingValue: Double) extends MakeInit[MyInit, Unit]:
  def initInner(id: InitId, params: Unit)(using parentId: ParentId, ctx: InitContext): MyInit =
    MyInit(id, ctx, value, backingValue)

final class ApiCalls:
  var process = 0
  var lastProcessDelta = 0.0
  var physicsProcess = 0
  var lastPhysicsDelta = 0.0
  var enterTree = 0
  var exitTree = 0
  var onFree = 0

final class ApiTestInit(val selfId: InitId, val ctx: InitContext, calls: ApiCalls) extends InitBase:
  override def process(delta: Double): Unit =
    calls.process += 1; calls.lastProcessDelta = delta
  override def physicsProcess(delta: Double): Unit =
    calls.physicsProcess += 1; calls.lastPhysicsDelta = delta
  override def enterTree(): Unit = calls.enterTree += 1
  override def exitTree(): Unit = calls.exitTree += 1
  override def onFree(): Unit = calls.onFree += 1

final class ApiTestBacking(calls: ApiCalls) extends MakeInit[ApiTestInit, Unit]:
  def initInner(id: InitId, params: Unit)(using parentId: ParentId, ctx: InitContext): ApiTestInit =
    ApiTestInit(id, ctx, calls)

final class TestInit(val selfId: InitId, val ctx: InitContext) extends InitBase

object Backing extends MakeInit[TestInit, Unit]:
  def initInner(id: InitId, params: Unit)(using parentId: ParentId, ctx: InitContext): TestInit =
    TestInit(id, ctx)

final class FreeCounter:
  var count = 0

final class TrackedInit(val selfId: InitId, val ctx: InitContext, counter: FreeCounter) extends InitBase:
  override def onFree(): Unit = counter.count += 1

final class TrackedBacking(counter: FreeCounter) extends MakeInit[TrackedInit, Unit]:
  def initInner(id: InitId, params: Unit)(using parentId: ParentId, ctx: InitContext): TrackedInit =
    TrackedInit(id, ctx, counter)

final class SiblingRef:
  var id: InitId = InitId.zero

final class FreesOnFreeInit(val selfId: InitId, val ctx: InitContext, sibling: SiblingRef)
    extends InitBase:
  override def onFree(): Unit = ctx.free(sibling.id)

final class FreesOnFreeBacking(sibling: SiblingRef) extends MakeInit[FreesOnFreeInit, Unit]:
  def initInner(id: InitId, params: Unit)(using parentId: ParentId, ctx: InitContext): FreesOnFreeInit =
    FreesOnFreeInit(id, ctx, sibling)

object TestSupport:
  def countInits(ctx: InitContext): Int =
    var n = 0
    ctx.forEach(_ => n += 1)
    n
