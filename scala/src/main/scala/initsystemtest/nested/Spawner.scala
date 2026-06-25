package initsystemtest.nested

import initsystem.*
import gdext.classes.Node
import gdext.api.GodotPrint

// Spawns enemy inits over time from `process`. The key trick: `process` is defined
// INSIDE `initInner`, so it captures `initInner`'s `given ParentId` (this spawner's
// own id) and `given InitContext`. Each `enemy.init(())` therefore registers the new
// enemy as a CHILD of this spawner — which is what makes freeing the spawner
// cascade-free every enemy it spawned (and each enemy's Rig + Health subtree).
case class Spawner(
  var enemy: Enemy,
  var maxEnemies: Int,
  var spawnInterval: Double // TODO PS add inside godot-scala-native a type Timestamp, so godot engine can serialize it that way
) extends Node with MakeInit[Spawner.Init, Unit]:
  def initInner(params: Unit)(using ParentId, InitContext): Spawner.Init =
    new Spawner.Init:
      GodotPrint.print(s"[Spawner] created (id=${selfId.value})")

      private var elapsed = 0.0
      private var spawned = 0
      private var done = false

      override def process(delta: Double): Unit =
        if done then ()
        else
          elapsed += delta
          if spawned < maxEnemies then
            if elapsed >= spawnInterval then
              elapsed = 0.0
              val e = enemy.init(()) // child of this spawner (captured ParentId)
              spawned += 1
              GodotPrint.print(s"[Spawner] spawned enemy #$spawned (id=${e.selfId.value})")
          else
            GodotPrint.print(
              s"[Spawner] freeing self; expect $spawned enemies (+ rig + health) to cascade-free"
            )
            done = true
            free()

      override def onFree(): Unit =
        GodotPrint.print(s"[Spawner] onFree (id=${selfId.value})")

object Spawner:
  trait Init extends InitBase
