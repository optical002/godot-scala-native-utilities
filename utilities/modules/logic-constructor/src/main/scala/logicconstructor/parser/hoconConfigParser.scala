package logicconstructor
package parser

import scala.util.control.NonFatal

import fastparse.Parsed
import org.akkajs.shocon.{Config => SC}  // the `Config` object inside the shocon package object
import org.akkajs.shocon.ConfigParser

import logicconstructor.ConfigValue.*

/** Parse a HOCON string into this module's [[ConfigValue]] ADT.
  *
  * Backed by the Scala-Native port of SHocon's runtime HOCON parser
  * (`org.akka-js:shocon-parser`). The Rust original used the `hocon-rs` crate; on
  * Scala Native no HOCON parser is published for `_native0.5_3` except this local
  * SHocon port, so we bridge SHocon's AST onto our own ADT here.
  */
def parseHocon(input: String): Either[String, ConfigValue] =
  try
    ConfigParser.parseString(input) match
      case Parsed.Success(value, _) => fromShocon(value)
      case f: Parsed.Failure        => Left(s"HOCON parse error: ${f.msg}")
  catch case NonFatal(e) => Left(s"HOCON parse error: ${e.getMessage}")

/** Bridge a SHocon AST node onto [[ConfigValue]].
  *
  * SHocon keeps unquoted scalars as `StringLiteral`, so we re-interpret them as
  * boolean / number / string the same way SHocon's own `unwrapped` does. SHocon's
  * `NullLiteral` has no counterpart in our ADT and is rejected.
  */
private def fromShocon(v: SC.Value): Either[String, ConfigValue] =
  v match
    case SC.Object(fields) =>
      fields.foldLeft[Either[String, Vector[(String, ConfigValue)]]](
        Right(Vector.empty)
      ) { case (acc, (k, fv)) =>
        for
          soFar <- acc
          cv <- fromShocon(fv)
        yield soFar :+ (k -> cv)
      }.map(CObj.apply)

    case SC.Array(elements) =>
      elements.foldLeft[Either[String, Vector[ConfigValue]]](
        Right(Vector.empty)
      ) { (acc, ev) =>
        for
          soFar <- acc
          cv <- fromShocon(ev)
        yield soFar :+ cv
      }.map(CArr.apply)

    case SC.BooleanLiteral(b) => Right(CBool(b))
    case SC.NumberLiteral(s)  => parseNumber(s)
    case SC.StringLiteral(s)  => Right(interpretScalar(s))
    case SC.NullLiteral =>
      Left("HOCON null is not representable in ConfigValue")

/** Re-interpret an unquoted scalar as boolean, then number, else a plain string
  * (mirrors SHocon's `StringLiteral.unwrapped`).
  */
private def interpretScalar(s: String): ConfigValue =
  s.trim match
    case "true" | "on" | "yes"  => CBool(true)
    case "false" | "off" | "no" => CBool(false)
    case other =>
      other.toDoubleOption match
        case Some(n) => CNum(n)
        case None    => CStr(s)

private def parseNumber(s: String): Either[String, ConfigValue] =
  s.toDoubleOption match
    case Some(n) => Right(CNum(n))
    case None    => Left(s"invalid HOCON number: $s")
