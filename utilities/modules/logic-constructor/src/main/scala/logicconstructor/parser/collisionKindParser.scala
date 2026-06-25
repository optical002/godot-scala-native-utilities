package logicconstructor
package parser

import logicconstructor.ConfigValue.CStr

private def parseSingleCollisionKind(s: String): Either[String, CollisionKind] =
  s.trim match
    case "Self"     => Right(CollisionKind.Self)
    case "SameKind" => Right(CollisionKind.SameKind)
    case "Other"    => Right(CollisionKind.Other)
    case other =>
      Left(
        s"Unknown CollisionKind: '$other'. Expected 'Self', 'SameKind', or 'Other'"
      )

/** Parse a [[ConfigValue]] into a [[CollisionKind]] bit-flag set.
  *
  * Accepts a string of one or more flag names separated by `|`:
  *   - single: `"Self"`
  *   - multiple: `"Self | Other"`, `"Self|SameKind|Other"`
  *
  * (HOCON's unquoted strings parse to [[ConfigValue.CStr]] too, so `Self`
  * without quotes works once the consumer's reader has produced a `CStr`.)
  */
def parseCollisionKind(value: ConfigValue): Either[String, CollisionKind] =
  value match
    case CStr(s) =>
      val parts = s.split('|').toSeq
      parts.foldLeft[Either[String, CollisionKind]](Right(CollisionKind.Empty)) {
        (acc, part) =>
          for
            soFar <- acc
            flag <- parseSingleCollisionKind(part)
          yield soFar | flag
      }
    case _ =>
      Left(
        s"CollisionKind parser expects a string (e.g., 'Self' or 'Self | Other'), got: $value"
      )
