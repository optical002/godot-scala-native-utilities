package logicconstructor

def runLca[T <: LcEntityType, Ctx](
    action: LcActionConfig[T, Ctx],
    source: LcEntity[T],
    target: LcEntity[T]
)(using ctx: Ctx): Unit =
  action.runLca(source, target)
