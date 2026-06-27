package godothoccon

import scala.concurrent.duration.{Duration, FiniteDuration, SECONDS}
import scala.util.control.NonFatal

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** A duration parsed from humantime/systemd-style strings: `"6s"`, `"500ms"`,
  * `"10m"`, `"1h 20m"`, `"2days"`. Whitespace-separated segments are summed, so
  * `"1h 20m"` is 80 minutes. Bare numbers are rejected — a duration must name
  * its unit.
  *
  * The Rust original leaned on the `humantime` crate. Scala's
  * [[scala.concurrent.duration.Duration]] only parses a single `<number><unit>`
  * segment, so this re-implements the multi-segment summing that humantime did.
  *
  * Ported from `framework/src/config/time_span.rs`.
  */
final case class TimeSpan(value: FiniteDuration):
  def secsF: Float = value.toUnit(SECONDS).toFloat

object TimeSpan:

  /** Parse a (possibly multi-segment) humantime duration string. Each
    * whitespace-separated segment is a single `Duration` literal (e.g. `"1h"`,
    * `"20m"`, `"500ms"`); the segments are summed.
    */
  def parse(s: String): Either[String, TimeSpan] =
    val trimmed = s.trim
    if trimmed.isEmpty then Left(s"invalid duration '$s': empty (e.g. \"6s\", \"10m\", \"1h 20m\")")
    else
      try
        val total = trimmed.split("\\s+").foldLeft(Duration.Zero: FiniteDuration): (acc, seg) =>
          Duration(seg) match
            case fd: FiniteDuration => acc + fd
            case other => throw IllegalArgumentException(s"segment '$seg' is not finite ($other)")
        Right(TimeSpan(total))
      catch
        case NonFatal(e) =>
          Left(s"invalid duration '$s': ${e.getMessage} (e.g. \"6s\", \"10m\", \"1h 20m\")")

  given ConfigReader[TimeSpan] = ConfigReader.fromCursor: cur =>
    cur.asString.flatMap: s =>
      parse(s) match
        case Right(ts) => Right(ts)
        case Left(msg) => cur.failed(CannotConvert(s, "TimeSpan", msg))
