package logicconstructor

/** The full configuration to run a single logical action — an ordered list of
  * [[LcSingleActionConfig]] (e.g. "deal 15 to others, then heal 4 to self").
  *
  * The Rust `From<Vec<…>>` impl is just the constructor here; [[dummy]] is the
  * empty config.
  */
final case class LcActionConfig[T <: LcEntityType](
    data: Seq[LcSingleActionConfig[T]]
)

object LcActionConfig:
  /** An empty action config that does nothing when run. */
  def dummy[T <: LcEntityType]: LcActionConfig[T] = LcActionConfig(Vector.empty)
