package logicconstructor

import pureconfig.ConfigReader

trait LcAction[A <: LcEntity.Type]:
  def apply(source: LcEntity[A], target: LcEntity[A]): Unit

object LcAction:
  final case class Config[A <: LcEntity.Type](
    data: Seq[Config.Single[A]]
  ):
    def runLca(
      source: LcEntity[A],
      target: LcEntity[A]
    ): Unit =
      for actionConfig <- data do
        val containsSelf = actionConfig.collision.contains(CollisionKind.Self)
        val containsSelfKind = actionConfig.collision.contains(CollisionKind.SameKind)
        val containsOthers = actionConfig.collision.contains(CollisionKind.Other)

        val isSameKind = source.typeId == target.typeId

        if containsSelf then actionConfig.action.apply(source, source)
        if containsSelfKind && isSameKind then actionConfig.action.apply(source, target)
        if containsOthers && !isSameKind then actionConfig.action.apply(source, target)

  object Config:
    def dummy[A <: LcEntity.Type]: Config[A] = Config(Vector.empty)

    final case class Single[A <: LcEntity.Type](
      action: LcAction[A],
      collision: CollisionKind,
    )

    final case class WithSource[A <: LcEntity.Type](
      config: LcAction.Config[A],
      source: LcEntity[A],
    ):
      def run(target: LcEntity[A]): Unit =
        config.runLca(source, target)

    /** Decodes a single action, accepting two HOCON forms:
      *
      *   - simple: `{ DealDamage = 25 }` — collision defaults to [[CollisionKind.Other]]
      *   - full:   `{ lca { DealDamage = 25 }, collision = "Self | Other" }`
      *
      * The effect itself (`DealDamage`, `Heal`, ...) is game-specific, so its [[ConfigReader]] is
      * supplied by the caller (typically `derives ConfigReader` on a sealed effect hierarchy).
      */
    given singleReader[A <: LcEntity.Type](using
      action: ConfigReader[LcAction[A]],
      collisionReader: ConfigReader[CollisionKind]
    ): ConfigReader[Single[A]] =
      ConfigReader.fromCursor { cur =>
        for
          obj <- cur.asObjectCursor
          isFullForm = obj.keys.toSet == Set("lca", "collision")
          single <-
            if isFullForm then
              for
                lca <- obj.atKey("lca").flatMap(action.from)
                collision <- obj.atKey("collision").flatMap(collisionReader.from)
              yield Single(lca, collision)
            else
              // simple form: the whole object is the effect; collision defaults to Other.
              action.from(cur).map(Single(_, CollisionKind.Other))
        yield single
      }

    /** Decodes a list of actions (mixed simple/full forms). */
    given listReader[A <: LcEntity.Type](using
      ConfigReader[LcAction[A]],
      ConfigReader[CollisionKind]
    ): ConfigReader[Config[A]] =
      ConfigReader[List[Single[A]]].map(Config(_))
