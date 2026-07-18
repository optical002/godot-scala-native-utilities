package initsystem

// Engine-independent test fixtures shared across the suites in this package.
// These factories carry their values directly and take no init params, so their
// Params type is Unit; the ParentId and InitContext arrive implicitly, and
// InitBase derives `selfId`/`ctx` from those givens (see InitBase.scala).

final class MyInit(val value: Double, val backingValue: Double)(using ParentId, InitContext)
    extends InitBase

final class MyBacking(value: Double, backingValue: Double) extends MakeInit[MyInit, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): MyInit =
    MyInit(value, backingValue)

final class ApiCalls:
  var process = 0
  var lastProcessDelta = 0.0
  var physicsProcess = 0
  var lastPhysicsDelta = 0.0
  var enterTree = 0
  var exitTree = 0
  var onFree = 0

final class ApiTestInit(calls: ApiCalls)(using ParentId, InitContext) extends InitBase:
  override def process(delta: Double): Unit =
    calls.process += 1; calls.lastProcessDelta = delta
  override def physicsProcess(delta: Double): Unit =
    calls.physicsProcess += 1; calls.lastPhysicsDelta = delta
  override def enterTree(): Unit = calls.enterTree += 1
  override def exitTree(): Unit = calls.exitTree += 1
  override def onFree(): Unit = calls.onFree += 1

final class ApiTestBacking(calls: ApiCalls) extends MakeInit[ApiTestInit, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): ApiTestInit =
    ApiTestInit(calls)

final class TestInit(using ParentId, InitContext) extends InitBase

object Backing extends MakeInit[TestInit, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): TestInit =
    TestInit()

final class FreeCounter:
  var count = 0

final class TrackedInit(counter: FreeCounter)(using ParentId, InitContext) extends InitBase:
  override def onFree(): Unit = counter.count += 1

final class TrackedBacking(counter: FreeCounter) extends MakeInit[TrackedInit, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): TrackedInit =
    TrackedInit(counter)

final class SiblingRef:
  var id: InitId = InitId.zero

final class FreesOnFreeInit(sibling: SiblingRef)(using ParentId, InitContext)
    extends InitBase:
  override def onFree(): Unit = ctx.free(sibling.id)

final class FreesOnFreeBacking(sibling: SiblingRef) extends MakeInit[FreesOnFreeInit, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): FreesOnFreeInit =
    FreesOnFreeInit(sibling)

object TestSupport:
  def countInits(ctx: InitContext): Int =
    var n = 0
    ctx.forEach(_ => n += 1)
    n
