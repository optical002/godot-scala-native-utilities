package logicconstructor

/** Marker for a game entity that can take part in logic-constructor actions.
  *
  * Game entities implement [[LcEntity.Type]] directly and are passed around as-is — there is no
  * wrapper type; `typeId` lives on the entity itself.
  */
object LcEntity:
  trait Type:
    def typeId: Type.Id
  object Type:
    type Id = Int
