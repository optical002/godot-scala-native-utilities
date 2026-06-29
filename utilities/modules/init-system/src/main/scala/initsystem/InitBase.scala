package initsystem

import gdext.classes.InputEvent

trait InitBase(using parentId: ParentId, ctx0: InitContext):
  def selfId: InitId = parentId
  def ctx: InitContext = ctx0

  def process(delta: Double): Unit = ()
  def physicsProcess(delta: Double): Unit = ()
  def enterTree(): Unit = ()
  def exitTree(): Unit = ()
  def input(event: InputEvent): Unit = ()
  def shortcutInput(event: InputEvent): Unit = ()
  def unhandledInput(event: InputEvent): Unit = ()
  def unhandledKeyInput(event: InputEvent): Unit = ()
  def onFree(): Unit = ()

  final def free(): Unit = ctx.free(selfId)
