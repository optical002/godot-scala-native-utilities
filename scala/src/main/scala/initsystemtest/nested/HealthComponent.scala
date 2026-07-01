package initsystemtest.nested

import initsystem.*
import gdext.api.Gd

// A component init that is NOT derived from a Godot node. It is a plain factory
// instantiated in code and composed into Player and Enemy.
case class HealthComponent(
  maxHp: Int
) extends MakeInit[HealthComponent.Init, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): HealthComponent.Init =
    new HealthComponent.Init:
      Gd.print(s"[Health] created maxHp=$maxHp (id=${selfId.value})")

      override def onFree(): Unit =
        Gd.print(s"[Health] onFree (id=${selfId.value})")

object HealthComponent:
  trait Init extends InitBase
