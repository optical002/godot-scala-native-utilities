package game.buffs

import godothoccon.{Id, Registry}

import logicconstructor.buffs.ActiveBuffs

object Compose:

  def composeEffectiveStats(
    base: DemoStats,
    active: ActiveBuffs[BuffConfig],
    registry: Registry[BuffConfig]
  ): DemoStats =
    active.buffs.foldLeft(base): (effective, buff) =>
      registry.get(buff.id) match
        case None => effective
        case Some(cfg) =>
          cfg.modifiers.foldLeft(effective)((eff, m) => m.apply(eff, base, buff.stacks))
