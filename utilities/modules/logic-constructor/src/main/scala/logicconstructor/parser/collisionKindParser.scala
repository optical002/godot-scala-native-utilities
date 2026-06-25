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
