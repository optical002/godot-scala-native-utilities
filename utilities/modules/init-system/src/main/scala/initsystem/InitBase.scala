package initsystem

import gdext.classes.InputEvent
import gdext.internal.engine.Gd

trait InitBase(using parentId: ParentId, ctx0: InitContext):
  def selfId: InitId = parentId
  def ctx: InitContext = ctx0

  def process(delta: Double): Unit = ()
  def physicsProcess(delta: Double): Unit = ()
  def enterTree(): Unit = ()
  def exitTree(): Unit = ()
  def input(event: Gd[InputEvent]): Unit = ()
  def shortcutInput(event: Gd[InputEvent]): Unit = ()
  def unhandledInput(event: Gd[InputEvent]): Unit = ()
  def unhandledKeyInput(event: Gd[InputEvent]): Unit = ()
  def onFree(): Unit = ()

  final def free(): Unit = ctx.free(selfId)
