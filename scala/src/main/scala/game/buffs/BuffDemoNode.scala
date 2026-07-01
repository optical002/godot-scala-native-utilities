package game.buffs

import java.io.File

import gdext.classes.Node
import gdext.api.Gd

import godothoccon.{Id, Registry, RegistryItem}

import logicconstructor.buffs.{ActiveBuffs, BuffLifecycle}

import DemoEntity.entity

final class BuffDemoNode extends Node:

  private given RegistryItem[BuffConfig] = RegistryItem.at("game/buffs")

  private val base = DemoStats(damage = 5.0f, moveSpeed = 100.0f)
  private val hero = entity(DemoEntity.Hero)
  private val noTarget = entity(DemoEntity.None)
  private given Unit = ()

  private var registry: Registry[BuffConfig] = Registry.empty[BuffConfig]
  private var active: ActiveBuffs[BuffConfig] = ActiveBuffs.empty[BuffConfig]
  private var running = false

  override def _ready(): Unit =
    findBuffsConfigDir() match
      case None =>
        Gd.print("[buff-demo] could not find a config dir containing game/buffs")
      case Some(dir) =>
        Registry.load[BuffConfig](dir) match
          case Left(failures) =>
            Gd.print(s"[buff-demo] failed to load buffs: ${failures.prettyPrint()}")
          case Right(reg) =>
            registry = reg
            Gd.print(s"[buff-demo] loaded ${reg.size} buff(s): ${reg.ids.map(_.value).mkString(", ")}")
            applyRage()
            running = true

  override def _process(delta: Double): Unit =
    if running then tick(delta.toFloat)

  private def applyRage(): Unit =
    val rage = Id[BuffConfig]("rage")
    registry.get(rage) match
      case None => Gd.print("[buff-demo] no 'rage' buff in registry")
      case Some(cfg) =>
        active = BuffLifecycle.applyBuff(active, rage, cfg.toSpec)
        cfg.onApply.value.runLca(hero, noTarget)
        logStats("applied rage")

  private def tick(delta: Float): Unit =
    val result = BuffLifecycle.tickBuffs(active, delta, lookup)
    active = result.active
    result.expired.foreach(chain => chain.runLca(hero, noTarget))
    if result.expired.nonEmpty then
      logStats("buff expired")
      running = false

  private def lookup(id: Id[BuffConfig]) =
    registry.get(id).map(_.toSpec)

  private def findBuffsConfigDir(): Option[File] =
    val subdir = summon[RegistryItem[BuffConfig]].subdir
    List("../../config/", "../config/", "config/")
      .map(File(_))
      .find(root => File(root, subdir).isDirectory)

  private def logStats(reason: String): Unit =
    val eff = Compose.composeEffectiveStats(base, active, registry)
    val activeNames = active.buffs.map: b =>
      val name = registry.get(b.id).map(_.visualsName).filter(_.nonEmpty).getOrElse(b.id.value)
      f"$name (${b.remaining}%.1fs x${b.stacks})"
    Gd.print(
      s"[buff-demo] $reason | damage=${eff.damage} moveSpeed=${eff.moveSpeed} | active=[${activeNames.mkString(", ")}]"
    )
