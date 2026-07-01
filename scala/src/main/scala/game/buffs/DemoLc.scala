package game.buffs

import gdext.api.Gd

import logicconstructor.ConfigValue as LcValue
import logicconstructor.ConfigValue.*
import logicconstructor.parser.ParseEffect
import logicconstructor.{LcAction, LcEntity, LcEntityType, LcEntityTypeId}

enum DemoEntity extends LcEntityType:
  case Hero
  case None

  def typeId: LcEntityTypeId = this match
    case Hero => 1
    case None => 0

object DemoEntity:
  def entity(e: DemoEntity): LcEntity[DemoEntity] = LcEntity(e)

type DemoCtx = Unit

final case class Log(message: String) extends LcAction[DemoEntity, DemoCtx]:
  def run(source: LcEntity[DemoEntity], target: LcEntity[DemoEntity])(using ctx: DemoCtx): Unit =
    Gd.print(s"[buff-demo] $message")

object DemoEffects:

  val parseEffect: ParseEffect[DemoEntity, DemoCtx] = (value: LcValue) =>
    value.asObject match
      case None => Left(s"expected object, got $value")
      case Some(fields) =>
        if fields.size != 1 then Left(s"expected single key, got ${fields.size}")
        else
          val (name, body) = fields.head
          name match
            case "Log" =>
              body.asString.toRight("Log expects a string").map(Log(_))
            case other => Left(s"unknown action: $other")
