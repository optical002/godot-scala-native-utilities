package logicconstructor

import logicconstructor.ConfigValue.{CNum, CObj}

object TestFixtures:

  final class Health(var value: Double)

  enum LcGameEntity extends LcEntityType:
    case Player(hp: Health)
    case Enemy(hp: Health)
    case Npc(age: Short)

    def typeId: LcEntityTypeId = this match
      case _: Player => 1
      case _: Enemy  => 2
      case _: Npc    => 3

    def maybeHealth: Option[Health] = this match
      case Player(hp) => Some(hp)
      case Enemy(hp)  => Some(hp)
      case Npc(_)     => None

  import LcGameEntity.*

  def entity(e: LcGameEntity): LcEntity[LcGameEntity] = LcEntity(e)

  // The test actions need no ambient capability, so they use a `Unit` ctx
  // (each suite brings `given Unit = ()` into scope to run them).
  final case class DealDamage(amount: Double) extends LcAction[LcGameEntity, Unit]:
    def run(
        source: LcEntity[LcGameEntity],
        target: LcEntity[LcGameEntity]
    )(using ctx: Unit): Unit =
      target.gameEntity.maybeHealth.foreach(h => h.value -= amount)

  final case class Heal(amount: Double) extends LcAction[LcGameEntity, Unit]:
    def run(
        source: LcEntity[LcGameEntity],
        target: LcEntity[LcGameEntity]
    )(using ctx: Unit): Unit =
      target.gameEntity.maybeHealth.foreach(h => h.value += amount)

  val parseGameEffect: parser.ParseEffect[LcGameEntity, Unit] = value =>
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
