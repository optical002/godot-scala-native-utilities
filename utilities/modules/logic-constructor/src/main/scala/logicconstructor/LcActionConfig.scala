package logicconstructor

final case class LcActionConfig[T <: LcEntityType, Ctx](
    data: Seq[LcSingleActionConfig[T, Ctx]]
):

  def runLca(source: LcEntity[T], target: LcEntity[T])(using ctx: Ctx): Unit =
    for actionConfig <- data do
      val containsSelf = actionConfig.collision.contains(CollisionKind.Self)
      val containsSelfKind = actionConfig.collision.contains(CollisionKind.SameKind)
      val containsOthers = actionConfig.collision.contains(CollisionKind.Other)

      val isSameKind = source.typeId == target.typeId

      if containsSelf then actionConfig.action.run(source, source)
      if containsSelfKind && isSameKind then actionConfig.action.run(source, target)
      if containsOthers && !isSameKind then actionConfig.action.run(source, target)

object LcActionConfig:

  def dummy[T <: LcEntityType, Ctx]: LcActionConfig[T, Ctx] = LcActionConfig(Vector.empty)
