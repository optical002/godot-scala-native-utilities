package logicconstructor

final case class LcSourceWithAction[T <: LcEntityType, Ctx](
    source: LcEntity[T],
    actionConfig: LcActionConfig[T, Ctx]
):

  def run(target: LcEntity[T])(using ctx: Ctx): Unit =
    runLca(actionConfig, source, target)
