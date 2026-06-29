package logicconstructor
package parser

def parseLcActionConfig[T <: LcEntityType](
    value: ConfigValue,
    parseEffect: ParseEffect[T]
): Either[String, LcActionConfig[T]] =
  parseLcConfigList(value, parseEffect).map(LcActionConfig(_))
