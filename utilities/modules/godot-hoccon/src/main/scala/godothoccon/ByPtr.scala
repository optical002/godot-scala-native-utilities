package godothoccon

import pureconfig.ConfigReader

/** A shared config payload compared by reference identity.
  *
  * The Rust original wrapped an `Rc<T>` whose `PartialEq` was `Rc::ptr_eq`, so
  * configs holding values that can't be compared structurally (e.g. boxed action
  * chains) could still derive equality for reactive change detection. This Scala
  * port wraps a value and overrides `equals`/`hashCode` to use reference identity
  * (`eq` / `System.identityHashCode`).
  *
  * Ported from `framework/src/config/by_ptr.rs`.
  */
final class ByPtr[A] private (val value: A):
  override def equals(other: Any): Boolean = other match
    case that: ByPtr[?] => this.value.asInstanceOf[AnyRef] eq that.value.asInstanceOf[AnyRef]
    case _ => false

  override def hashCode(): Int = System.identityHashCode(value)

  override def toString: String = s"ByPtr($value)"

object ByPtr:
  def apply[A](value: A): ByPtr[A] = new ByPtr(value)

  given [A](using reader: ConfigReader[A]): ConfigReader[ByPtr[A]] = reader.map(ByPtr(_))
