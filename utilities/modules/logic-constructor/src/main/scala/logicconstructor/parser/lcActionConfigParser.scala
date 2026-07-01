package logicconstructor
package parser

def parseLcActionConfig[T <: LcEntityType, Ctx](
    value: ConfigValue,
    parseEffect: ParseEffect[T, Ctx]
): Either[String, LcActionConfig[T, Ctx]] =
  parseLcConfigList(value, parseEffect).map(LcActionConfig(_))
