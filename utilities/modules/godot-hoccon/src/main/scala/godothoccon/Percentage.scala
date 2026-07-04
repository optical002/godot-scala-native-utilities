package godothoccon

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** A percentage parsed from a `%`-suffixed string: `80%`, `14.5%`, `"14,5%"`
  * (comma decimals work too, but need quotes in HOCON since `,` terminates an
  * unquoted value). Bare numbers are rejected — a percentage must say so.
  * Values must be `>= 0%` but may exceed `100%` (e.g. a `150%` damage bonus →
  * fraction `1.5`).
  *
  * Stored as a fraction: `80%` => `Percentage(0.8f)`.
  *
  * Ported from `framework/src/config/percentage.rs`.
  */
final case class Percentage(value: Float):
  def fraction: Float = value

object Percentage:
  val Zero: Percentage = Percentage(0.0f)
  val Certain: Percentage = Percentage(1.0f)

  def parse(raw: String): Either[String, Percentage] =
    val s = raw.trim
    if !s.endsWith("%") then Left(s"'$s' must end with '%' (e.g. \"80%\", \"14.5%\")")
    else
      s.stripSuffix("%").trim.replace(',', '.').toFloatOption match
        case Some(percent) if percent >= 0.0f => Right(Percentage(percent / 100.0f))
        case Some(_) => Left(s"'$s' must be at least 0%")
        case None => Left(s"'$s' is not a valid percentage")

  given ConfigReader[Percentage] = ConfigReader.fromCursor: cur =>
    cur.asString.flatMap: raw =>
      parse(raw) match
        case Right(p) => Right(p)
        case Left(msg) => cur.failed(CannotConvert(raw, "Percentage", msg))
