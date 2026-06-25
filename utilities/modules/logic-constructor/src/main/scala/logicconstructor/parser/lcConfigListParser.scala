package logicconstructor
package parser

import logicconstructor.ConfigValue.CArr

def parseLcConfigListRaw(value: ConfigValue): Either[String, Seq[LcConfigRaw]] =
  value match
    case CArr(items) =>
      items.zipWithIndex.foldLeft[Either[String, Vector[LcConfigRaw]]](
        Right(Vector.empty)
      ) { case (acc, (item, index)) =>
        for
          soFar <- acc
          config <- parseLcConfigRaw(item).left.map(e =>
            s"Failed to parse LcaConfig at index $index: $e"
          )
        yield soFar :+ config
      }
    case _ =>
      Left(s"LcaConfig list parser expects an array, got: $value")

def parseLcConfigList[T <: LcEntityType](
    value: ConfigValue,
    parseEffect: ParseEffect[T]
): Either[String, Seq[LcSingleActionConfig[T]]] =
  value match
    case CArr(items) =>
      items.zipWithIndex.foldLeft[Either[String, Vector[LcSingleActionConfig[T]]]](
        Right(Vector.empty)
      ) { case (acc, (item, index)) =>
        for
          soFar <- acc
          config <- parseLcConfig(item, parseEffect).left.map(e =>
            s"Failed to parse LcaConfig at index $index: $e"
          )
        yield soFar :+ config
      }
    case _ =>
      Left(s"LcaConfig list parser expects an array, got: $value")
