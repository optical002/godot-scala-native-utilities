package logicconstructor

final case class LcActionConfig[T <: LcEntityType](
    data: Seq[LcSingleActionConfig[T]]
)

object LcActionConfig:

  def dummy[T <: LcEntityType]: LcActionConfig[T] = LcActionConfig(Vector.empty)
