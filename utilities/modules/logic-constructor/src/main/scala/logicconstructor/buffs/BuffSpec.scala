package logicconstructor.buffs

import godothoccon.{ByPtr, TimeSpan}

import logicconstructor.{LcActionConfig, LcEntityType}

final case class BuffSpec[T <: LcEntityType, Ctx, Mods](
    duration: TimeSpan,
    reApply: ReApplyBehaviour,
    onApply: ByPtr[LcActionConfig[T, Ctx]],
    onRemove: ByPtr[LcActionConfig[T, Ctx]],
    mods: Mods
)
