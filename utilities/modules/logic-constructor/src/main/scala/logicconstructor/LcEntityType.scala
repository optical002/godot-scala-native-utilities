package logicconstructor

/** A stable, per-type id used to decide collisions between entities.
  *
  * It identifies the *type*, not the instance: every Player shares one
  * `LcEntityTypeId`, every Enemy another. [[runLca]] compares source and target
  * ids to pick the [[CollisionKind]] arm (same id ⇒ same kind).
  */
type LcEntityTypeId = Int

/** The co-product of everything logic-constructor can operate on.
  *
  * A consumer's "entity registry" (typically a Scala `enum` or sealed trait)
  * extends this and returns a distinct [[typeId]] per case. The Rust crate's
  * `LcEntityType` trait was implemented on the consumer's enum the same way.
  */
trait LcEntityType:
  /** This entity's concrete type id. Must be unique per entity type. */
  def typeId: LcEntityTypeId
