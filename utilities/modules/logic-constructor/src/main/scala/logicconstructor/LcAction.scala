package logicconstructor

/** A single action a source performs onto a target.
  *
  * The Rust trait carried a `clone_box` method so `Box<dyn LcAction>` could be
  * cloned past the borrow checker. Scala traits are reference types, so configs
  * just hold an `LcAction[T]` directly and that whole apparatus is gone.
  */
trait LcAction[T <: LcEntityType]:
  /** Apply this action from `source` onto `target`. */
  def apply(source: LcEntity[T], target: LcEntity[T]): Unit
