package logicconstructor

final case class LcEntity[A <: LcEntity.Type](gameEntity: A):
  def typeId: LcEntity.Type.Id = gameEntity.typeId

object LcEntity:
  trait Type:
    def typeId: Type.Id
  object Type:
    type Id = Int
