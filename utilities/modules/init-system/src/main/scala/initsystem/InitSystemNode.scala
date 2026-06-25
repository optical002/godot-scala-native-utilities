package initsystem

import gdext.classes.{InputEvent, Node}
import gdext.internal.engine.Gd
import gdext.api.GodotPrint

class InitSystemNode extends Node:

  val ctx: InitContext = InitContext()

  override def _ready(): Unit =
    ctx.processPending()

  override def _process(delta: Double): Unit =
    ctx.processPending()
    ctx.forEach(_.process(delta))

  override def _physics_process(delta: Double): Unit =
    ctx.processPending()
    ctx.forEach(_.physicsProcess(delta))

  override def _enter_tree(): Unit =
    ctx.forEach(_.enterTree())
    ctx.processPending()

  override def _exit_tree(): Unit =
    ctx.forEach(_.exitTree())
    ctx.processPending()

  override def _input(event: Gd[InputEvent]): Unit =
    ctx.forEach(_.input(event))

  override def _shortcut_input(event: Gd[InputEvent]): Unit =
    ctx.forEach(_.shortcutInput(event))

  override def _unhandled_input(event: Gd[InputEvent]): Unit =
    ctx.forEach(_.unhandledInput(event))

  override def _unhandled_key_input(event: Gd[InputEvent]): Unit =
    ctx.forEach(_.unhandledKeyInput(event))
