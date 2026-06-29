package logicconstructor

final case class LcSourceWithAction[T <: LcEntityType](
    source: LcEntity[T],
    actionConfig: LcActionConfig[T]
):

  def run(target: LcEntity[T]): Unit =
    runLca(actionConfig, source, target)
