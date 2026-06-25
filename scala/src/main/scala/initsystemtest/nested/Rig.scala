package initsystemtest.nested

import initsystem.*
import gdext.classes.Node
import gdext.api.GodotPrint

// A leaf sub-init owned by an entity (Player / Enemy). It is a Godot node so it
// can be configured in the editor, but the Init it produces is just a lightweight
// object living in the InitContext.
case class Rig(
  var label: String
) extends Node with MakeInit[Rig.Init, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): Rig.Init =
    new Rig.Init:
      GodotPrint.print(s"[Rig:$label] created (id=${selfId.value})")

      override def onFree(): Unit =
        GodotPrint.print(s"[Rig:$label] onFree (id=${selfId.value})")

object Rig:
  trait Init extends InitBase
