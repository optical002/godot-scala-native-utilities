package initsystemtest

import gdext.classes.Node
import gdext.api.GodotPrint
import initsystem.*

case class InitEntry(
  var initTestNode: InitTestNode,
  var initSystemNode: InitSystemNode,
) extends Node:
  given ParentId = ParentId.root
  given ctx: InitContext = initSystemNode.ctx

  override def _ready(): Unit = 
    // Test does init works.
    val init = initTestNode.init(25)
    ctx.processPending()
    GodotPrint.print(s"Init gold is now_: ${init.totalGold}")

    // Test get by instance id.
    val instanceId = initTestNode.instanceId
    GodotPrint.print(s"instance Id: ${instanceId.toI64} and init id: ${init.selfId.value}")
    val initFromCtx = ctx.getInitA[InitTestNode.Init](instanceId).get 
    GodotPrint.print(s"Init gold getting from ctx by instance id: ${initFromCtx.totalGold}")

