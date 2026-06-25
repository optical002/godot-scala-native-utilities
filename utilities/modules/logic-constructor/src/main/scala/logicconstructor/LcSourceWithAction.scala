package logicconstructor

/** Ties a source entity to an action config so it can be run against any
  * target with a single [[run]] call.
  */
final case class LcSourceWithAction[T <: LcEntityType](
    source: LcEntity[T],
    actionConfig: LcActionConfig[T]
):
  /** Run the bound action config from [[source]] onto `target`. */
  def run(target: LcEntity[T]): Unit =
    runLca(actionConfig, source, target)
