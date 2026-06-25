package logicconstructor

def runLca[T <: LcEntityType](
    action: LcActionConfig[T],
    source: LcEntity[T],
    target: LcEntity[T]
): Unit =
  for actionConfig <- action.data do
    val containsSelf = actionConfig.collision.contains(CollisionKind.Self)
    val containsSelfKind = actionConfig.collision.contains(CollisionKind.SameKind)
    val containsOthers = actionConfig.collision.contains(CollisionKind.Other)

    val isSameKind = source.typeId == target.typeId

    if containsSelf then actionConfig.action.apply(source, source)
    if containsSelfKind && isSameKind then actionConfig.action.apply(source, target)
    if containsOthers && !isSameKind then actionConfig.action.apply(source, target)
