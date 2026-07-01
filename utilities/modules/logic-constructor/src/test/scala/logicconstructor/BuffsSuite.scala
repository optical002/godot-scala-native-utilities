package logicconstructor

import scala.concurrent.duration.*

import godothoccon.{ByPtr, Id, TimeSpan}
import pureconfig.ConfigSource

import logicconstructor.buffs.*
import TestFixtures.*
import TestFixtures.LcGameEntity.*

class BuffsSuite extends munit.FunSuite:

    given Unit = ()

    type Buff
    type Mods = Unit

    private def id(s: String): Id[Buff] = Id[Buff](s)

    private def dummyChain: LcActionConfig[LcGameEntity, Unit] =
        LcActionConfig.dummy[LcGameEntity, Unit]

    private def chain(action: LcAction[LcGameEntity, Unit]): LcActionConfig[LcGameEntity, Unit] =
        LcActionConfig(Vector(LcSingleActionConfig(action, CollisionKind.Self)))

    private def spec(
        seconds: Int,
        reApply: ReApplyBehaviour,
        onRemove: LcActionConfig[LcGameEntity, Unit] = dummyChain
    ): BuffSpec[LcGameEntity, Unit, Mods] =
        BuffSpec(
            duration = TimeSpan(seconds.seconds),
            reApply = reApply,
            onApply = ByPtr(dummyChain),
            onRemove = ByPtr(onRemove),
            mods = ()
        )

    test("applyBuff adds a new buff at full duration with one stack") {
        val rage = id("rage")
        val active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, spec(5, ReApplyBehaviour.RefreshDuration))
        assertEquals(active.buffs.size, 1)
        val b = active.buffs.head
        assertEquals(b.id, rage)
        assertEquals(b.remaining, 5.0f)
        assertEquals(b.stacks, 1)
    }

    test("re-apply RefreshDuration resets remaining, stacks stay 1") {
        val rage = id("rage")
        val s = spec(5, ReApplyBehaviour.RefreshDuration)
        val once = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        val ticked = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](once, 2.0f, _ => Some(s)).active
        assertEquals(ticked.buffs.head.remaining, 3.0f)
        val reapplied = BuffLifecycle.applyBuff(ticked, rage, s)
        assertEquals(reapplied.buffs.size, 1)
        assertEquals(reapplied.buffs.head.remaining, 5.0f)
        assertEquals(reapplied.buffs.head.stacks, 1)
    }

    test("re-apply Stacks bumps the stack count and caps at max") {
        val rage = id("rage")
        val s = spec(5, ReApplyBehaviour.Stacks(3, StackExpiry.ReduceStack))
        var active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        assertEquals(active.buffs.head.stacks, 1)
        active = BuffLifecycle.applyBuff(active, rage, s)
        assertEquals(active.buffs.head.stacks, 2)
        active = BuffLifecycle.applyBuff(active, rage, s)
        assertEquals(active.buffs.head.stacks, 3)
        active = BuffLifecycle.applyBuff(active, rage, s)
        assertEquals(active.buffs.head.stacks, 3)
    }

    test("tickBuffs decrements remaining while the buff survives") {
        val rage = id("rage")
        val s = spec(5, ReApplyBehaviour.RefreshDuration)
        val active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        val r = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](active, 1.5f, _ => Some(s))
        assertEquals(r.active.buffs.size, 1)
        assertEquals(r.active.buffs.head.remaining, 3.5f)
        assertEquals(r.expired, Nil)
    }

    test("expiry drops the buff and returns its onRemove chain") {
        val rage = id("rage")
        val marker = chain(DealDamage(7.0))
        val s = spec(5, ReApplyBehaviour.RefreshDuration, onRemove = marker)
        val active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        val r = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](active, 6.0f, _ => Some(s))
        assertEquals(r.active.buffs, Nil)
        assertEquals(r.expired.size, 1)

        val hp = Health(100.0)
        val e = entity(Player(hp))
        r.expired.foreach(c => runLca(c, e, e))
        assertEquals(hp.value, 93.0)
    }

    test("Stacks/ReduceStack keeps a stack and resets remaining instead of expiring") {
        val rage = id("rage")
        val s = spec(5, ReApplyBehaviour.Stacks(3, StackExpiry.ReduceStack))
        var active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        active = BuffLifecycle.applyBuff(active, rage, s) // stacks = 2
        val r = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](active, 6.0f, _ => Some(s))
        assertEquals(r.expired, Nil)
        assertEquals(r.active.buffs.size, 1)
        assertEquals(r.active.buffs.head.stacks, 1)
        assertEquals(r.active.buffs.head.remaining, 5.0f)
    }

    test("Stacks/ReduceStack at one stack expires and emits onRemove") {
        val rage = id("rage")
        val marker = chain(DealDamage(4.0))
        val s = spec(5, ReApplyBehaviour.Stacks(3, StackExpiry.ReduceStack), onRemove = marker)
        val active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s) // stacks = 1
        val r = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](active, 6.0f, _ => Some(s))
        assertEquals(r.active.buffs, Nil)
        assertEquals(r.expired.size, 1)
    }

    test("a buff with no spec in the lookup is silently dropped on expiry") {
        val rage = id("rage")
        val s = spec(5, ReApplyBehaviour.RefreshDuration)
        val active = BuffLifecycle.applyBuff(ActiveBuffs.empty[Buff], rage, s)
        val r = BuffLifecycle.tickBuffs[Buff, LcGameEntity, Unit, Mods](active, 6.0f, _ => None)
        assertEquals(r.active.buffs, Nil)
        assertEquals(r.expired, Nil)
    }

    // --- ReApplyBehaviour / StackExpiry ConfigReader ---

    private def loadReApply(body: String): Either[String, ReApplyBehaviour] =
        ConfigSource.string(s"x = $body").at("x").load[ReApplyBehaviour].left.map(_.prettyPrint())

    test("ReApplyBehaviour parses RefreshDuration") {
        assertEquals(loadReApply("RefreshDuration"), Right(ReApplyBehaviour.RefreshDuration))
    }

    test("ReApplyBehaviour parses a Stacks body") {
        assertEquals(
            loadReApply("{ Stacks { max: 3, on-expire: ReduceStack } }"),
            Right(ReApplyBehaviour.Stacks(3, StackExpiry.ReduceStack))
        )
        assertEquals(
            loadReApply("{ Stacks { max: 2, on-expire: RemoveBuff } }"),
            Right(ReApplyBehaviour.Stacks(2, StackExpiry.RemoveBuff))
        )
    }

    test("ReApplyBehaviour rejects bare Stacks and unknown variants") {
        assert(loadReApply("Stacks").isLeft)
        assert(loadReApply("Bogus").isLeft)
        assert(loadReApply("{ Stacks { max: 0, on-expire: ReduceStack } }").isLeft)
    }
