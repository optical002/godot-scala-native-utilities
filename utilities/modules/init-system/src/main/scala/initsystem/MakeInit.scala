package initsystem

import gdext.internal.register.GodotScriptClass

trait MakeInit[I <: InitBase, P]:
  def initInner(params: P)(using ParentId, InitContext): I

object MakeInit:
  extension [I <: InitBase, P](self: MakeInit[I, P])
    def init(params: P)(using ParentId, InitContext): I =
      makeAndRegister(self, InitId.random(), params, summon[ParentId])
 
  extension [T <: GodotScriptClass & MakeInit[I, P], I <: InitBase, P](self: T)
    def init(params: P)(using ParentId, InitContext): I =
      makeAndRegister(self, self.instanceId, params, summon[ParentId])

  private def makeAndRegister[I <: InitBase, P](
    factory: MakeInit[I, P],
    id: InitId,
    params: P,
    parentId: ParentId
  )(using ctx: InitContext): I =
    // In here when we current id as a parentId, and previous parentId we pass in to ctx to create
    // the parent child relationship.
    given ParentId = ParentId(id)
    val init = factory.initInner(params)
    ctx.addInit(parentId, id, init)
    init


