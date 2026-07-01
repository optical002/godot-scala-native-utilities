package logicconstructor.buffs

import godothoccon.Id

final case class ActiveBuff[C](id: Id[C], remaining: Float, stacks: Int)

final case class ActiveBuffs[C](buffs: List[ActiveBuff[C]] = Nil)

object ActiveBuffs:
    def empty[C]: ActiveBuffs[C] = ActiveBuffs(Nil)
