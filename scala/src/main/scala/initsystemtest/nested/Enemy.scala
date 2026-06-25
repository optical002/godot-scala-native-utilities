package initsystemtest.nested

import initsystem.*
import gdext.classes.Node
import gdext.api.GodotPrint

// An entity spawned at runtime by the Spawner. The children created inside
// `initInner` auto-parent to this Enemy init (via the injected `given ParentId`),
// so freeing an enemy cascades to its Rig and Health.
case class Enemy(
  var rig: Rig
) extends Node with MakeInit[Enemy.Init, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): Enemy.Init =
    rig.init(())                     // child of Enemy
    HealthComponent(50).init(()) // child of Enemy

    new Enemy.Init:
      GodotPrint.print(s"[Enemy] created (id=${selfId.value})")

      override def onFree(): Unit =
        GodotPrint.print(s"[Enemy] onFree (id=${selfId.value})")

object Enemy:
  trait Init extends InitBase
