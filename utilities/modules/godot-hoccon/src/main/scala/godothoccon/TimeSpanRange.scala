package godothoccon

import scala.concurrent.duration.FiniteDuration

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** A `from..to` window over two [[TimeSpan]] durations, parsed from a single
  * string: `"0m..1m"`, `"30s..5m"`, `"1h..1h 30m"`. Each side is a humantime
  * duration (see [[TimeSpan]]). Both `..` and `..=` split the same way — for a
  * continuous time window the distinction is meaningless, so they are
  * equivalent (unlike integer [[RangeConfig]]).
  *
  * Validation: the start must not come after the end (`from <= to`). Equal
  * endpoints are allowed (an empty window that never contains anything).
  *
  * Ported from `framework/src/config/time_span_range.rs`.
  */
final case class TimeSpanRange(from: TimeSpan, to: TimeSpan):

  /** Half-open containment `[from, to)`, so adjacent windows that share a
    * boundary don't both claim it.
    */
  def contains(t: FiniteDuration): Boolean =
    from.value <= t && t < to.value

object TimeSpanRange:

  def parse(s: String): Either[String, TimeSpanRange] =
    val splitIdx = s.indexOf("..=") match
      case -1 => s.indexOf("..")
      case i => i
    if splitIdx < 0 then Left(s"range string must use '..' syntax (e.g. \"0m..1m\"), got: \"$s\"")
    else
      val sep = if s.startsWith("..=", splitIdx) then 3 else 2
      val lhs = s.substring(0, splitIdx)
      val rhs = s.substring(splitIdx + sep)
      for
        from <- TimeSpan.parse(lhs.trim).left.map(e => s"range start: $e")
        to <- TimeSpan.parse(rhs.trim).left.map(e => s"range end: $e")
        result <-
          if from.value > to.value then Left(s"range start ${from.value} must not be after end ${to.value}")
          else Right(TimeSpanRange(from, to))
      yield result

  given ConfigReader[TimeSpanRange] = ConfigReader.fromCursor: cur =>
    cur.asString.flatMap: s =>
      parse(s) match
        case Right(r) => Right(r)
        case Left(msg) => cur.failed(CannotConvert(s, "TimeSpanRange", msg))
