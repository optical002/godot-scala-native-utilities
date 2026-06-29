package logicconstructor
package parser

import logicconstructor.ConfigValue.CObj

type ParseEffect[T <: LcEntityType] = ConfigValue => Either[String, LcAction[T]]

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

def parseLcConfig[T <: LcEntityType](
    value: ConfigValue,
    parseEffect: ParseEffect[T]
): Either[String, LcSingleActionConfig[T]] =
  for
    raw <- parseLcConfigRaw(value)
    effect <- parseEffect(raw.effectValue)
  yield LcSingleActionConfig(action = effect, collision = raw.collision)
