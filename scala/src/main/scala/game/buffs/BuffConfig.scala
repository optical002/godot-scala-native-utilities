package game.buffs

import com.typesafe.config.{ConfigValue as TsValue, ConfigValueType}
import godothoccon.{ByPtr, TimeSpan}
import pureconfig.{ConfigCursor, ConfigReader}
import pureconfig.error.CannotConvert

import logicconstructor.ConfigValue as LcValue
import logicconstructor.ConfigValue.*
import logicconstructor.LcActionConfig
import logicconstructor.buffs.{BuffSpec, ReApplyBehaviour}
import logicconstructor.parser.parseLcActionConfig

final case class BuffMods(modifiers: List[StatModifier], visualsName: String)

type DemoActionConfig = LcActionConfig[DemoEntity, DemoCtx]

final case class BuffConfig(
  duration: TimeSpan,
  reApply: ReApplyBehaviour,
  modifiers: List[StatModifier],
  onApply: ByPtr[DemoActionConfig],
  onRemove: ByPtr[DemoActionConfig],
  visualsName: String
):

  def toSpec: BuffSpec[DemoEntity, DemoCtx, BuffMods] =
    BuffSpec(duration, reApply, onApply, onRemove, BuffMods(modifiers, visualsName))

object BuffConfig:

  given ConfigReader[ByPtr[DemoActionConfig]] = ConfigReader.fromCursor: cur =>
    toLcValue(cur).flatMap(v => parseLcActionConfig(v, DemoEffects.parseEffect)) match
      case Right(cfg) => Right(ByPtr(cfg))
      case Left(msg)  => cur.failed(CannotConvert("<actions>", "LcActionConfig", msg))

  given ConfigReader[BuffConfig] = ConfigReader.fromCursor: cur =>
    cur.asObjectCursor.flatMap: obj =>
      for
        durationCur <- obj.atKey("duration")
        duration <- summon[ConfigReader[TimeSpan]].from(durationCur)
        reApply <- obj.atKeyOrUndefined("re-apply") match
          case c if c.isUndefined => Right(ReApplyBehaviour.RefreshDuration)
          case c                  => summon[ConfigReader[ReApplyBehaviour]].from(c)
        modifiersCur <- obj.atKey("modifiers")
        modifiers <- summon[ConfigReader[List[StatModifier]]].from(modifiersCur)
        onApply <- obj.atKeyOrUndefined("on-apply") match
          case c if c.isUndefined => Right(ByPtr(LcActionConfig.dummy[DemoEntity, DemoCtx]))
          case c                  => summon[ConfigReader[ByPtr[DemoActionConfig]]].from(c)
        onRemove <- obj.atKeyOrUndefined("on-remove") match
          case c if c.isUndefined => Right(ByPtr(LcActionConfig.dummy[DemoEntity, DemoCtx]))
          case c                  => summon[ConfigReader[ByPtr[DemoActionConfig]]].from(c)
        visualsName <- obj.atKeyOrUndefined("visuals") match
          case c if c.isUndefined => Right("")
          case c =>
            c.asObjectCursor.flatMap: v =>
              v.atKeyOrUndefined("name") match
                case n if n.isUndefined => Right("")
                case n                  => n.asString
      yield BuffConfig(duration, reApply, modifiers, onApply, onRemove, visualsName)

  private def toLcValue(cur: ConfigCursor): Either[String, LcValue] =
    cur.asConfigValue match
      case Right(cv) => Right(fromTypesafe(cv))
      case Left(f)   => Left(f.prettyPrint())

  private def fromTypesafe(value: TsValue): LcValue =
    value.valueType() match
      case ConfigValueType.OBJECT =>
        val obj = value.asInstanceOf[com.typesafe.config.ConfigObject]
        val fields = scala.collection.mutable.ArrayBuffer.empty[(String, LcValue)]
        val it = obj.entrySet().iterator()
        while it.hasNext do
          val e = it.next()
          fields += (e.getKey -> fromTypesafe(e.getValue))
        CObj(fields.toVector)
      case ConfigValueType.LIST =>
        val list = value.asInstanceOf[com.typesafe.config.ConfigList]
        val items = scala.collection.mutable.ArrayBuffer.empty[LcValue]
        val it = list.iterator()
        while it.hasNext do items += fromTypesafe(it.next())
        CArr(items.toVector)
      case ConfigValueType.NUMBER =>
        CNum(value.unwrapped.asInstanceOf[Number].doubleValue())
      case ConfigValueType.BOOLEAN =>
        CBool(value.unwrapped.asInstanceOf[java.lang.Boolean].booleanValue())
      case ConfigValueType.STRING =>
        CStr(value.unwrapped.toString)
      case ConfigValueType.NULL =>
        CStr("")
      case _ =>
        CStr(value.render())
