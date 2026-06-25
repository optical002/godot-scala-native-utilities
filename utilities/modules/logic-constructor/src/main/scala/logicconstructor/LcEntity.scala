package logicconstructor

/** An entity logic-constructor operates on — a thin wrapper around the
  * consumer's game entity ([[gameEntity]]) carrying its [[LcEntityType]].
  *
  * The Rust `LcEntity<T>` wrapped `game_entity: T`; this is the same shape, and
  * [[typeId]] forwards to the wrapped entity so [[runLca]] can compare kinds.
  */
final case class LcEntity[T <: LcEntityType](gameEntity: T):
  /** The wrapped entity's type id. */
  def typeId: LcEntityTypeId = gameEntity.typeId
