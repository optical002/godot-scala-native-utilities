package logicconstructor

trait LcAction[T <: LcEntityType]:

  def apply(source: LcEntity[T], target: LcEntity[T]): Unit
