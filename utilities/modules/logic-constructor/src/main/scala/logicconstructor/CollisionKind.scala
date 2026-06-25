package logicconstructor

/** Which source→target relationships an action fires against.
  *
  * A small bit-flag set (the Rust crate used the `bitflags!` macro over a
  * `u8`). Three flags, combinable with [[CollisionKind.|]]:
  *
  *   - [[CollisionKind.Self]] — source acts on itself.
  *   - [[CollisionKind.SameKind]] — source acts on a target of the same
  *     [[LcEntityType.typeId]].
  *   - [[CollisionKind.Other]] — source acts on a target of a different type.
  *
  * @see [[runLca]] for how each flag drives application.
  */
opaque type CollisionKind = Int

object CollisionKind:
  /** The empty set (no relationships). */
  val Empty: CollisionKind = 0

  /** Self collision. */
  val Self: CollisionKind = 0x1

  /** Same-type collision. */
  val SameKind: CollisionKind = 0x2

  /** Different-type collision. */
  val Other: CollisionKind = 0x4

  extension (self: CollisionKind)
    /** Union of two flag sets. */
    infix def |(other: CollisionKind): CollisionKind =
      (self: Int) | (other: Int)

    /** Whether `self` includes every flag in `other`. */
    def contains(other: CollisionKind): Boolean =
      ((self: Int) & (other: Int)) == (other: Int)

    /** The raw bits, for debugging/tests. */
    def bits: Int = self
