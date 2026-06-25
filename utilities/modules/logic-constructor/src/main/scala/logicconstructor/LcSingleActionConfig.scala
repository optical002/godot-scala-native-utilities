package logicconstructor

/** One action plus the [[CollisionKind]] gating when it fires.
  *
  * The Rust struct held a `Box<dyn LcAction<T>>` and hand-wrote `Clone` via
  * `clone_box`; here it is a plain case class holding the action by reference.
  */
final case class LcSingleActionConfig[T <: LcEntityType](
    action: LcAction[T],
    collision: CollisionKind
)

/** Untyped intermediate produced by the config parser.
  *
  * The library cannot know which concrete [[LcAction]] a consumer wants, so a
  * raw config carries the un-parsed effect [[ConfigValue]] alongside the parsed
  * collision flags. Consumers finalize this into [[LcSingleActionConfig]] by
  * feeding [[effectValue]] through their own effect parser (see
  * [[parseLcConfig]]).
  */
final case class LcConfigRaw(
    effectValue: ConfigValue,
    collision: CollisionKind
)
