package initsystem

import scala.collection.mutable
import scala.reflect.ClassTag
import gdext.api.GodotPrint

trait InitContext:
  def getInit(id: InitId): Option[InitBase]
  def getInitA[A <: InitBase : ClassTag](id: InitId): Option[A] =
    getInit(id).collect:
      case a: A => a
  def addInit(parentId: InitId, id: InitId, init: InitBase): Unit
  def free(id: InitId): Unit
  def forEach(f: InitBase => Unit): Unit
  def processPending(): Unit

object InitContext:
  def apply(): InitContext = new InitContext:

    private val initCollection  = mutable.LinkedHashMap.empty[InitId, InitBase]
    private val childrenOf      = mutable.LinkedHashMap.empty[InitId, mutable.ArrayBuffer[InitId]]
    private val pendingAddition = mutable.LinkedHashMap.empty[InitId, PendingAddition]
    private val pendingRemoval  = mutable.LinkedHashSet.empty[InitId]

    def addInit(parentId: InitId, id: InitId, init: InitBase): Unit =
      if pendingAddition.contains(id) then
        GodotPrint.printWarning(
          s"[init-system] Trying to add a second time the same init with id: ${id.value}"
        )
      else
        pendingAddition(id) = PendingAddition(parentId, init)

    def free(id: InitId): Unit =
      if !pendingRemoval.add(id) then
        GodotPrint.printWarning(
          s"[init-system] Trying to free a second time the same init with id: ${id.value}"
        )

    def processPending(): Unit =
      pendingAddition.filterInPlace((id, _) => !pendingRemoval.contains(id))

      for (id, PendingAddition(parentId, init)) <- pendingAddition do
        if initCollection.contains(id) then
          GodotPrint.printWarning(
            s"[init-system] Trying to add init which already exists inside 'initCollection' with id ${id.value}"
          )
        else
          initCollection(id) = init
        childrenOf.getOrElseUpdate(parentId, mutable.ArrayBuffer.empty) += id
      pendingAddition.clear()

      val toRemove = mutable.LinkedHashSet.from(pendingRemoval)
      val frontier = mutable.Stack.from(pendingRemoval)
      while frontier.nonEmpty do
        val id = frontier.pop()
        for children <- childrenOf.get(id); child <- children do
          if toRemove.add(child) then frontier.push(child)

      for id <- toRemove do
        initCollection.remove(id).foreach(_.onFree())

      for id <- toRemove do childrenOf.remove(id)
      for children <- childrenOf.valuesIterator do
        children.filterInPlace(child => !toRemove.contains(child))

      pendingRemoval --= toRemove

    def getInit(id: InitId): Option[InitBase] = initCollection.get(id)

    def forEach(f: InitBase => Unit): Unit =
      initCollection.valuesIterator.foreach(f)

  private final case class PendingAddition(parentId: InitId, init: InitBase)
