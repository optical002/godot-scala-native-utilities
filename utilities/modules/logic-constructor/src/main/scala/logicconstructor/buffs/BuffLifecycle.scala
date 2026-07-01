package logicconstructor.buffs

import godothoccon.Id

import logicconstructor.{LcActionConfig, LcEntityType}

final case class TickResult[C, T <: LcEntityType, Ctx](
    active: ActiveBuffs[C],
    expired: List[LcActionConfig[T, Ctx]]
)

object BuffLifecycle:

    def applyBuff[C, T <: LcEntityType, Ctx, Mods](
        active: ActiveBuffs[C],
        id: Id[C],
        spec: BuffSpec[T, Ctx, Mods]
    ): ActiveBuffs[C] =
        val full = spec.duration.secsF
        active.buffs.find(_.id == id) match
            case Some(_) =>
                val updated = active.buffs.map: b =>
                    if b.id == id then
                        val newStacks = spec.reApply match
                            case ReApplyBehaviour.Stacks(max, _) => math.min(b.stacks + 1, max)
                            case _                               => b.stacks
                        b.copy(remaining = full, stacks = newStacks)
                    else b
                ActiveBuffs(updated)
            case None =>
                ActiveBuffs(active.buffs :+ ActiveBuff(id, full, 1))

    def tickBuffs[C, T <: LcEntityType, Ctx, Mods](
        active: ActiveBuffs[C],
        delta: Float,
        lookup: Id[C] => Option[BuffSpec[T, Ctx, Mods]]
    ): TickResult[C, T, Ctx] =
        val expired = scala.collection.mutable.ListBuffer.empty[LcActionConfig[T, Ctx]]
        val kept = active.buffs.flatMap: buff =>
            val remaining = buff.remaining - delta
            if remaining > 0.0f then List(buff.copy(remaining = remaining))
            else
                lookup(buff.id) match
                    case None => Nil
                    case Some(spec) =>
                        val reduceOne = spec.reApply match
                            case ReApplyBehaviour.Stacks(_, StackExpiry.ReduceStack) => true
                            case _                                                   => false
                        if reduceOne && buff.stacks > 1 then
                            List(buff.copy(stacks = buff.stacks - 1, remaining = spec.duration.secsF))
                        else
                            expired += spec.onRemove.value
                            Nil
        TickResult(ActiveBuffs(kept), expired.toList)
