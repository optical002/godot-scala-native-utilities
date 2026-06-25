package initsystemtest.nested

import initsystem.*
import gdext.classes.Node
import gdext.api.GodotPrint

// Entry node + root of the init chain. Holds the InitSystemNode (source of the
// InitContext) plus the Player and Spawner nodes. On `_ready` it bootstraps itself
// as the root init; its `initInner` creates the Player and Spawner inits as its
// children. The resulting chain:
//
//   World
//   ├─ Player ─ (Rig, Health)
//   └─ Spawner ─ Enemy* ─ (Rig, Health)   // enemies spawned at runtime
//
// Freeing the Spawner cascade-frees its enemies (and their subtrees) while leaving
// World and Player intact — observable via the [*] onFree logs.
case class World(
  var initSystemNode: InitSystemNode,
  var player: Player,
  var spawner: Spawner
) extends Node with MakeInit[World.Init, Unit]:
  given ctx: InitContext = initSystemNode.ctx

  override def _ready(): Unit =
    given ParentId = ParentId.root
    this.init(())
    ctx.processPending()
    GodotPrint.print("[World] bootstrapped")

  def initInner(params: Unit)(using ParentId, InitContext): World.Init =
    val playerInit = player.init(())   // child of World (node id)
    val spawnerInit = spawner.init(()) // child of World (node id)

    new World.Init:
      GodotPrint.print(s"[World] created (id=${selfId.value})")

      override def onFree(): Unit =
        GodotPrint.print(s"[World] onFree (id=${selfId.value})")

object World:
  trait Init extends InitBase
