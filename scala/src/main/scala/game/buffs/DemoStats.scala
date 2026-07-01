package game.buffs

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

final case class DemoStats(damage: Float = 0.0f, moveSpeed: Float = 0.0f):

  def field(stat: StatKind): Float = stat match
    case StatKind.Damage    => damage
    case StatKind.MoveSpeed => moveSpeed

  def add(stat: StatKind, delta: Float): DemoStats = stat match
    case StatKind.Damage    => copy(damage = damage + delta)
    case StatKind.MoveSpeed => copy(moveSpeed = moveSpeed + delta)

enum StatKind:
  case Damage, MoveSpeed

object StatKind:

  given ConfigReader[StatKind] = ConfigReader.fromCursor: cur =>
    cur.asString.flatMap: name =>
      values.find(_.toString == name) match
        case Some(k) => Right(k)
        case None =>
          cur.failed(CannotConvert(name, "StatKind", "expected Damage or MoveSpeed"))

enum ModifierValue:
  case Raw(value: Float)
  case Percentage(fraction: Float)

object ModifierValue:

  given ConfigReader[ModifierValue] = ConfigReader.fromCursor: cur =>
    cur.asObjectCursor.flatMap: obj =>
      if obj.keys.size != 1 then
        cur.failed(CannotConvert(
          obj.keys.mkString("{", ", ", "}"),
          "ModifierValue",
          "expected exactly one of Raw or Percentage"
        ))
      else
        val name = obj.keys.head
        obj.atKey(name).flatMap: body =>
          name match
            case "Raw"        => body.asFloat.map(ModifierValue.Raw(_))
            case "Percentage" => body.asFloat.map(ModifierValue.Percentage(_))
            case other =>
              cur.failed(CannotConvert(other, "ModifierValue", "expected Raw or Percentage"))

final case class StatModifier(stat: StatKind, value: ModifierValue) derives ConfigReader:

  def apply(stats: DemoStats, base: DemoStats, count: Int): DemoStats =
    val perStack = value match
      case ModifierValue.Raw(v)        => v
      case ModifierValue.Percentage(p) => base.field(stat) * p
    stats.add(stat, perStack * count)
