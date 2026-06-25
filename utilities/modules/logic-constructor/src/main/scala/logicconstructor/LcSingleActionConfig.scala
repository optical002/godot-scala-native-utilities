package logicconstructor

final case class LcSingleActionConfig[T <: LcEntityType](
    action: LcAction[T],
    collision: CollisionKind
)

final case class LcConfigRaw(
    effectValue: ConfigValue,
    collision: CollisionKind
)
