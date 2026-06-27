package godothoccon

/** A tiny shareable xorshift64 generator for gameplay randomness (not
  * cryptographic). Instances share mutable state, so passing one around and
  * pulling from it advances the same stream (mirroring the Rust `Rc<Cell<u64>>`
  * sharing).
  *
  * Ported from `framework/src/rng.rs`.
  */
final class XorShiftRng private (private var state: Long):

  /** Next raw 64-bit value (advances the stream). */
  def nextU64(): Long =
    var x = state
    x ^= x << 13
    x ^= x >>> 7
    x ^= x << 17
    state = x
    x

  /** Uniform-ish float in `[0, 1)`. */
  def unitFloat(): Float =
    // Use the unsigned magnitude so the result stays in [0, 1).
    val v = nextU64() >>> 11 // 53 high bits, always non-negative
    (v.toDouble / (1L << 53).toDouble).toFloat

  /** Uniform-ish float in `[-1, 1)`. */
  def unitFloatSigned(): Float = unitFloat() * 2.0f - 1.0f

  /** Uniform-ish integer in `min..=max` (inclusive). */
  def rangeInt(min: Int, max: Int): Int =
    if min >= max then min
    else
      val span = (max.toLong - min.toLong) + 1L
      val r = math.floorMod(nextU64(), span)
      (min.toLong + r).toInt

object XorShiftRng:
  def apply(seed: Long): XorShiftRng =
    new XorShiftRng(if seed == 0L then 0x9E3779B97F4A7C15L else seed)
