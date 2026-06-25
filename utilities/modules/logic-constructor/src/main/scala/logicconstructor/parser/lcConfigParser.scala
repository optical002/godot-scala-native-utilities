package logicconstructor
package parser

import logicconstructor.ConfigValue.CObj

/** An effect parser: turns the un-parsed effect [[ConfigValue]] of one config
  * entry into a concrete [[LcAction]]. The library owns no effect-type
  * knowledge — the consumer supplies this. Mirrors the Rust
  * `Fn(&Value) -> Result<Box<dyn LcAction<T>>, String>` closure.
  */
type ParseEffect[T <: LcEntityType] = ConfigValue => Either[String, LcAction[T]]

/** Parse a [[ConfigValue]] into an [[LcConfigRaw]], preserving the un-parsed
  * effect value. Two accepted forms:
  *
  *   1. Simple: `{ DealDamage: 10 }` — the whole object is the effect value;
  *      collision defaults to [[CollisionKind.Other]].
  *   2. Full: `{ lca: { DealDamage: 10 }, collision: "Self" }` — `lca` is the
  *      effect value, `collision` is parsed via [[parseCollisionKind]].
  *
  * The library does not interpret the effect value; consumers do that with
  * their own [[ParseEffect]] when finalizing into [[LcSingleActionConfig]].
  */
def parseLcConfigRaw(value: ConfigValue): Either[String, LcConfigRaw] =
  value match
    case obj @ CObj(_) =>
      (obj.get("lca"), obj.get("collision")) match
        case (Some(lcaValue), Some(collisionValue)) =>
          parseCollisionKind(collisionValue).map { collision =>
            LcConfigRaw(effectValue = lcaValue, collision = collision)
          }
        case (Some(_), None) =>
          Left("LcaConfig requires 'collision' field")
        case (None, Some(_)) =>
          Left("LcaConfig requires 'lca' field")
        case (None, None) =>
          Right(LcConfigRaw(effectValue = value, collision = CollisionKind.Other))
    case _ =>
      Left(s"LcaConfig parser expects an object, got: $value")

/** Finalize a [[ConfigValue]] into a typed [[LcSingleActionConfig]] by invoking
  * `parseEffect` on the effect value. The closure owns all effect-type
  * knowledge — the library only handles collision-kind plumbing.
  */
def parseLcConfig[T <: LcEntityType](
    value: ConfigValue,
    parseEffect: ParseEffect[T]
): Either[String, LcSingleActionConfig[T]] =
  for
    raw <- parseLcConfigRaw(value)
    effect <- parseEffect(raw.effectValue)
  yield LcSingleActionConfig(action = effect, collision = raw.collision)
