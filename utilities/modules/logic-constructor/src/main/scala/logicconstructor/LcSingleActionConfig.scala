package logicconstructor

final case class LcSingleActionConfig[T <: LcEntityType, Ctx](
    action: LcAction[T, Ctx],
    collision: CollisionKind
)

final case class LcConfigRaw(
    effectValue: ConfigValue,
    collision: CollisionKind
)
