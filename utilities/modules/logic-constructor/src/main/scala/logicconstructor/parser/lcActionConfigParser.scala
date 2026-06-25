package logicconstructor
package parser

/** Parse a [[ConfigValue]] array into a typed [[LcActionConfig]], using
  * `parseEffect` for each effect body. Thin wrapper over [[parseLcConfigList]].
  */
def parseLcActionConfig[T <: LcEntityType](
    value: ConfigValue,
    parseEffect: ParseEffect[T]
): Either[String, LcActionConfig[T]] =
  parseLcConfigList(value, parseEffect).map(LcActionConfig(_))
