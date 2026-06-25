package initsystem

import java.util.UUID
import scala.util.Random
import gdext.api.InstanceId

opaque type InitId = Long

object InitId:
  val zero: InitId = 0

  def random(): InitId =
    val msb = (Random.nextLong() & ~0xf000L) | 0x4000L          // version 4
    val lsb = (Random.nextLong() & ~(0xc000L << 48)) | (0x8000L << 48) // variant
    new UUID(msb, lsb).getLeastSignificantBits

  given Conversion[InstanceId, InitId] with
    def apply(instanceId: InstanceId): InitId =
      instanceId.toI64

  extension (self: InitId)
    def value: Long = self

opaque type ParentId <: InitId = InitId
object ParentId:
  val root: ParentId = InitId.zero

  private[initsystem] def apply(id: InitId): ParentId = id

  extension (self: ParentId)
    def id: InitId = self
