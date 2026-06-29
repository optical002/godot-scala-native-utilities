package logicconstructor

final case class LcEntity[T <: LcEntityType](gameEntity: T):

  def typeId: LcEntityTypeId = gameEntity.typeId
