package logicconstructor

trait LcAction[T <: LcEntityType, Ctx]:

  def run(source: LcEntity[T], target: LcEntity[T])(using ctx: Ctx): Unit
