package logicconstructor

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

opaque type CollisionKind = Int

object CollisionKind:
  val Empty    : CollisionKind = 0
  val Self     : CollisionKind = 1 << 0
  val SameKind : CollisionKind = 1 << 1
  val Other    : CollisionKind = 1 << 2

  private def parseFlag(name: String): Either[String, CollisionKind] =
    name.trim match
      case "Self"     => Right(Self)
      case "SameKind" => Right(SameKind)
      case "Other"    => Right(Other)
      case other =>
        Left(s"Unknown CollisionKind: '$other'. Expected 'Self', 'SameKind', or 'Other'")

  /** Decodes a pipe-separated set of flags, e.g. `"Self"` or `"Self | Other"`. */
  given ConfigReader[CollisionKind] =
    ConfigReader.fromString { s =>
      s.split('|').foldLeft[Either[String, CollisionKind]](Right(Empty)) { (acc, part) =>
        for
          soFar <- acc
          flag <- parseFlag(part)
        yield soFar | flag
      }.left.map(CannotConvert(s, "CollisionKind", _))
    }

  extension (self: CollisionKind)

    inline infix def |(other: CollisionKind): CollisionKind =
      (self: Int) | (other: Int)

    inline infix def &(other: CollisionKind): CollisionKind =
      (self: Int) & (other: Int)

    inline infix def ^(other: CollisionKind): CollisionKind =
      (self: Int) ^ (other: Int)

    inline def unary_~ : CollisionKind =
      ~(self: Int)

    inline def contains(flag: CollisionKind): Boolean =
      (self & flag) == flag

    inline def containsAny(flags: CollisionKind): Boolean =
      (self & flags) != Empty

    inline def isEmpty: Boolean =
      self == Empty

    inline def bits: Int =
      self
