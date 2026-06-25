package logicconstructor

import logicconstructor.ConfigValue.{CNum, CObj}

/** Port of the Rust `tests/fixture.rs` game model: three entity kinds, two
  * actions (DealDamage / Heal), and an effect parser.
  *
  * Rust modelled health as `Rc<RefCell<f32>>` so clones shared one cell; here a
  * tiny mutable `Health` box plays the same role (entities are value-y, but the
  * health box is shared by reference across copies).
  */
object TestFixtures:

  /** A shared mutable health cell — stands in for Rust's `Rc<RefCell<f32>>`. */
  final class Health(var value: Double)

  // --- Entity co-product (the consumer's "registry") ---
  enum LcGameEntity extends LcEntityType:
    case Player(hp: Health)
    case Enemy(hp: Health)
    case Npc(age: Short)

    def typeId: LcEntityTypeId = this match
      case _: Player => 1
      case _: Enemy  => 2
      case _: Npc    => 3

    /** Health for entities that have it (Npc has none). */
    def maybeHealth: Option[Health] = this match
      case Player(hp) => Some(hp)
      case Enemy(hp)  => Some(hp)
      case Npc(_)     => None

  import LcGameEntity.*

  // Convenience: wrap a game entity as an LcEntity (Rust's `From` impls).
  def entity(e: LcGameEntity): LcEntity[LcGameEntity] = LcEntity(e)

  // --- Actions ---
  final case class DealDamage(amount: Double) extends LcAction[LcGameEntity]:
    def apply(
        source: LcEntity[LcGameEntity],
        target: LcEntity[LcGameEntity]
    ): Unit =
      target.gameEntity.maybeHealth.foreach(h => h.value -= amount)

  final case class Heal(amount: Double) extends LcAction[LcGameEntity]:
    def apply(
        source: LcEntity[LcGameEntity],
        target: LcEntity[LcGameEntity]
    ): Unit =
      target.gameEntity.maybeHealth.foreach(h => h.value += amount)

  /** The consumer's effect parser — `{ DealDamage: 15 }` ⇒ `DealDamage(15)`. */
  val parseGameEffect: parser.ParseEffect[LcGameEntity] = value =>
    value match
      case CObj(fields) if fields.size == 1 =>
        val (name, inner) = fields.head
        inner match
          case CNum(amount) =>
            name match
              case "DealDamage" => Right(DealDamage(amount))
              case "Heal"       => Right(Heal(amount))
              case other        => Left(s"unknown effect: $other")
          case _ => Left(s"$name expects a number")
      case CObj(fields) => Left(s"expected single key, got ${fields.size}")
      case _            => Left(s"expected object, got $value")
