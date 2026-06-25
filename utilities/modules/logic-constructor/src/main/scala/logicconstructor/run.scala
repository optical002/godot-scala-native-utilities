package logicconstructor

/** Run an [[LcActionConfig]] from `source` onto `target`.
  *
  * For each [[LcSingleActionConfig]], the [[CollisionKind]] flags decide which
  * applications fire, exactly as in the Rust `run_lca`:
  *
  *   - [[CollisionKind.Self]] — apply source→source (regardless of target).
  *   - [[CollisionKind.SameKind]] — apply source→target when their type ids match.
  *   - [[CollisionKind.Other]] — apply source→target when their type ids differ.
  *
  * Flags combine, so e.g. `Self | Other` fires both the self arm and (when the
  * kinds differ) the other arm.
  */
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
