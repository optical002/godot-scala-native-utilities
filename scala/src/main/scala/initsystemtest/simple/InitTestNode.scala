package initsystemtest.simple

import initsystem.*
import gdext.classes.{InputEvent, Node}
import gdext.internal.engine.Gd
import gdext.api.GodotPrint

case class InitTestNode(
  var gold: Int
) extends Node with MakeInit[InitTestNode.Init, Int]:
  def initInner(
    params: Int
  )(using ParentId, InitContext): InitTestNode.Init =
    new InitTestNode.Init:
      val totalGold = gold + params 

      override def process(delta: Double): Unit = GodotPrint.print(s"from the process: $delta")

object InitTestNode:
  trait Init extends InitBase:
    def totalGold: Int

