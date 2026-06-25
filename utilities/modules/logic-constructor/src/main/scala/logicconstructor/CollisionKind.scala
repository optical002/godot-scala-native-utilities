package logicconstructor

opaque type CollisionKind = Int

object CollisionKind:

  val Empty: CollisionKind = 0

  val Self: CollisionKind = 0x1

  val SameKind: CollisionKind = 0x2

  val Other: CollisionKind = 0x4

  extension (self: CollisionKind)

    infix def |(other: CollisionKind): CollisionKind =
      (self: Int) | (other: Int)

    def contains(other: CollisionKind): Boolean =
      ((self: Int) & (other: Int)) == (other: Int)

    def bits: Int = self
