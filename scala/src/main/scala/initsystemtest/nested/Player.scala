package initsystemtest.nested

import initsystem.*
import gdext.classes.Node
import gdext.api.GodotPrint

// A single (non-prototype) entity. The children created inside `initInner`
// auto-parent to this Player init via the `given ParentId` the framework injects,
// so freeing Player cascades to its Rig and Health.
case class Player(
  var rig: Rig
) extends Node with MakeInit[Player.Init, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): Player.Init =
    rig.init(())                      // child of Player
    HealthComponent(100).init(()) // child of Player

    new Player.Init:
      GodotPrint.print(s"[Player] created (id=${selfId.value})")

      override def onFree(): Unit =
        GodotPrint.print(s"[Player] onFree (id=${selfId.value})")

object Player:
  trait Init extends InitBase
