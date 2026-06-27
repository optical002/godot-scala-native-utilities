package godothoccon

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** An inclusive integer `[min, max]` range. Parsed from these shapes:
  *   - `3`       => min=3, max=3 (fixed value)
  *   - `"3..6"`  => min=3, max=5 (exclusive upper, matches Rust `..`)
  *   - `"3..=6"` => min=3, max=6 (inclusive upper)
  *
  * Validation: `min > 0` and `max >= min`.
  *
  * The Rust original was generic over a sealed `RangeValue` (only `u32`); this
  * port fixes the element type to non-negative `Int`.
  *
  * Ported from `framework/src/config/range.rs`.
  */
final case class RangeConfig(min: Int, max: Int)

object RangeConfig:

  private def parseInt(s: String): Either[String, Int] =
    s.trim.toIntOption.toRight(s"'$s' is not an integer")

  private def parseRangeStr(s: String): Either[String, (Int, Int)] =
    val trimmed = s.trim
    val parts: Either[String, (String, String, Boolean)] =
      trimmed.indexOf("..=") match
        case i if i >= 0 => Right((trimmed.substring(0, i), trimmed.substring(i + 3), true))
        case _ =>
          trimmed.indexOf("..") match
            case i if i >= 0 => Right((trimmed.substring(0, i), trimmed.substring(i + 2), false))
            case _ => Left(s"range string must use '..' or '..=' syntax, got: \"$s\"")
    parts.flatMap: (lhs, rhs, inclusive) =>
      for
        min <- parseInt(lhs).left.map(e => s"range min: $e")
        upper <- parseInt(rhs).left.map(e => s"range max: $e")
        max <-
          if inclusive then Right(upper)
          else if upper < 1 then Left("exclusive upper bound must be > 0")
          else Right(upper - 1)
      yield (min, max)

  /** Validate `min > 0` and `max >= min`. */
  private def validate(min: Int, max: Int): Either[String, RangeConfig] =
    if min <= 0 then Left(s"min ($min) must be greater than 0")
    else if max < min then Left(s"max ($max) must be >= min ($min)")
    else Right(RangeConfig(min, max))

  given ConfigReader[RangeConfig] = ConfigReader.fromCursor: cur =>
    val parsed: Either[String, (Int, Int)] =
      cur.asInt match
        case Right(v) => Right((v, v))
        case Left(_) =>
          cur.asString match
            case Right(s) => parseRangeStr(s)
            case Left(_) => Left("must be a number or range string (e.g. \"3..6\")")
    parsed.flatMap((min, max) => validate(min, max)) match
      case Right(rc) => Right(rc)
      case Left(msg) => cur.failed(CannotConvert("?", "RangeConfig", msg))
