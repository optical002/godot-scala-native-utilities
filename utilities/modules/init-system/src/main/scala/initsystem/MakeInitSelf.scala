package initsystem

/** Like [[MakeInit]], but for inits that ARE their own backing: the init class
  * extends [[InitBase]] directly (no separate `Init` trait) and its companion
  * object extends `MakeInitSelf[TheClass, P]` to act as the factory.
  *
  * Give the init class a `private` constructor so the only way to build one is
  * through the companion's `.init`, which registers it with the init-system:
  *
  * {{{
  * final class CollisionTracker private (
  *   cooldown: Float,
  *   active: mutable.Map[(Long, Long), Float]
  * )(using ParentId, InitContext) extends InitBase:
  *   override def process(delta: Double): Unit = ???
  *
  * object CollisionTracker extends MakeInitSelf[CollisionTracker, Float]:
  *   def initInner(params: Float)(using ParentId, InitContext): CollisionTracker =
  *     new CollisionTracker(cooldown = params, active = mutable.Map.empty)
  * }}}
  *
  * Call site:
  * {{{
  * val tracker  = CollisionTracker.init(config.cooldown) // registers + returns
  * val tracker2 = CollisionTracker(config.cooldown, ...)  // does NOT compile
  * }}}
  *
  * This is intended for inits that do NOT derive from a Godot `Node`; keep using
  * [[MakeInit]] for node-backed inits.
  */
trait MakeInitSelf[I <: InitBase, P]:
  def initInner(params: P)(using ParentId, InitContext): I

object MakeInitSelf:
  extension [I <: InitBase, P](self: MakeInitSelf[I, P])
    def init(params: P)(using parentId: ParentId, ctx: InitContext): I =
      val id = InitId.random()
      given ParentId = ParentId(id)
      val init = self.initInner(params)
      ctx.addInit(parentId, id, init)
      init
