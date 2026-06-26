package logicconstructor

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

object TestFixtures:

  final class Health(var value: Double)

  enum LcGameEntity extends LcEntity.Type:
    case Player(hp: Health)
    case Enemy(hp: Health)
    case Npc(age: Short)

    def typeId: LcEntity.Type.Id = this match
      case _: Player => 1
      case _: Enemy  => 2
      case _: Npc    => 3

    def maybeHealth: Option[Health] = this match
      case Player(hp) => Some(hp)
      case Enemy(hp)  => Some(hp)
      case Npc(_)     => None

  import LcGameEntity.*

  def entity(e: LcGameEntity): LcEntity[LcGameEntity] = LcEntity(e)

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

  /** The game-specific effect reader: a single-key object whose key names the effect and whose
    * value is the amount, e.g. `{ DealDamage = 25 }`. The effect set is inherently app-specific
    * (it produces concrete `LcAction` instances), so this reader lives with the game's fixtures —
    * the library itself stays fully derivation-driven (see `LcAction.Config` / `CollisionKind`).
    */
  given ConfigReader[LcAction[LcGameEntity]] =
    ConfigReader.fromCursor { cur =>
      for
        obj <- cur.asObjectCursor
        name <- obj.keys.toList match
          case single :: Nil => Right(single)
          case other =>
            cur.failed(CannotConvert(other.mkString("{", ",", "}"), "LcAction", "expected a single effect key"))
        amount <- obj.atKey(name).flatMap(_.asDouble)
        action <- name match
          case "DealDamage" => Right(DealDamage(amount))
          case "Heal"       => Right(Heal(amount))
          case unknown      => cur.failed(CannotConvert(unknown, "LcAction", "unknown effect"))
      yield action
    }
