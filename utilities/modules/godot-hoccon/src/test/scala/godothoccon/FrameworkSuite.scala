package godothoccon

import scala.concurrent.duration.*

import gdext.builtin.Color
import pureconfig.ConfigSource

/** Ports the Rust `framework` config unit tests (percentage.rs, range.rs,
  * time_span_range.rs, random_table.rs) plus coverage for the remaining value
  * types, driven through pureconfig the way a consuming game would.
  */
class FrameworkSuite extends munit.FunSuite:

  /** Decode a value from a single-key HOCON doc `x = <body>`. */
  private def load[A](body: String)(using pureconfig.ConfigReader[A]): Either[String, A] =
    ConfigSource.string(s"x = $body").at("x").load[A].left.map(_.prettyPrint())

  // --- Percentage (percentage.rs) ---

  test("Percentage parses percents as fractions") {
    assertEquals(load[Percentage]("\"60%\""), Right(Percentage(0.6f)))
    assertEquals(load[Percentage]("\"100%\""), Right(Percentage(1.0f)))
    assertEquals(load[Percentage]("\"0%\""), Right(Percentage.Zero))
  }

  test("Percentage allows values above 100%") {
    assertEquals(load[Percentage]("\"150%\""), Right(Percentage(1.5f)))
  }

  test("Percentage rejects a bare number") {
    assert(load[Percentage]("60").isLeft)
  }

  test("Percentage rejects negatives") {
    assert(load[Percentage]("\"-5%\"").isLeft)
  }

  // --- TimeSpan (time_span.rs) ---

  test("TimeSpan parses single-unit durations") {
    assertEquals(load[TimeSpan]("\"6s\"").map(_.value), Right(6.seconds))
    assertEquals(load[TimeSpan]("\"500ms\"").map(_.value), Right(500.millis))
    assertEquals(load[TimeSpan]("\"10m\"").map(_.value), Right(10.minutes))
  }

  test("TimeSpan sums multi-segment durations") {
    assertEquals(load[TimeSpan]("\"1h 20m\"").map(_.value), Right(80.minutes))
  }

  test("TimeSpan rejects a bare number") {
    assert(load[TimeSpan]("60").isLeft)
  }

  // --- TimeSpanRange (time_span_range.rs) ---

  test("TimeSpanRange parses a range") {
    val r = load[TimeSpanRange]("\"0m..1m\"").toOption.get
    assertEquals(r.from, TimeSpan(0.seconds))
    assertEquals(r.to, TimeSpan(60.seconds))
  }

  test("TimeSpanRange treats ..= as equivalent to ..") {
    assertEquals(load[TimeSpanRange]("\"0s..1m\""), load[TimeSpanRange]("\"0s..=1m\""))
  }

  test("TimeSpanRange allows equal endpoints") {
    val r = load[TimeSpanRange]("\"1m..1m\"").toOption.get
    assertEquals(r.from, r.to)
  }

  test("TimeSpanRange rejects from after to") {
    assert(load[TimeSpanRange]("\"5m..1m\"").isLeft)
  }

  test("TimeSpanRange has half-open containment") {
    val r = load[TimeSpanRange]("\"1m..2m\"").toOption.get
    assertEquals(r.contains(59.seconds), false)
    assertEquals(r.contains(60.seconds), true)
    assertEquals(r.contains(119.seconds), true)
    assertEquals(r.contains(120.seconds), false)
  }

  // --- RangeConfig (range.rs) ---

  test("RangeConfig treats a bare number as a fixed range") {
    assertEquals(load[RangeConfig]("3"), Right(RangeConfig(3, 3)))
  }

  test("RangeConfig parses exclusive ranges") {
    assertEquals(load[RangeConfig]("\"3..6\""), Right(RangeConfig(3, 5)))
  }

  test("RangeConfig parses inclusive ranges") {
    assertEquals(load[RangeConfig]("\"3..=6\""), Right(RangeConfig(3, 6)))
  }

  test("RangeConfig rejects a min of zero") {
    assert(load[RangeConfig]("0").isLeft)
  }

  test("RangeConfig rejects max < min") {
    assert(load[RangeConfig]("\"6..=3\"").isLeft)
  }

  // --- ColorConfig (color.rs) ---

  test("ColorConfig parses 6-digit hex") {
    import ColorConfig.given
    assertEquals(load[Color]("\"#ffffff\""), Right(Color(1.0f, 1.0f, 1.0f, 1.0f)))
    assertEquals(load[Color]("\"#000000\""), Right(Color(0.0f, 0.0f, 0.0f, 1.0f)))
  }

  test("ColorConfig parses alpha-first 8-digit hex") {
    import ColorConfig.given
    assertEquals(load[Color]("\"#80ffffff\"").map(_.a), Right(128.0f / 255.0f))
  }

  test("ColorConfig rejects invalid hex") {
    import ColorConfig.given
    assert(load[Color]("\"#xyz\"").isLeft)
  }

  // --- ByPtr (by_ptr.rs) ---

  test("ByPtr compares by reference identity") {
    val s = "shared"
    assertEquals(ByPtr(s), ByPtr(s))
    assertNotEquals(ByPtr(new String("a")), ByPtr(new String("a")))
  }

  // --- ChanceTable (random_table.rs) ---

  test("ChanceTable parses entries and shorthand certain entries") {
    val table = ConfigSource
      .string("""
        drops {
          ChanceTable = [
            { item = "coins", chance = "60%" },
            "magnet"
          ]
        }
      """)
      .at("drops")
      .load[ChanceTable[String]]
      .toOption
      .get
    assertEquals(
      table.entries,
      List(ChanceEntry("coins", Percentage(0.6f)), ChanceEntry("magnet", Percentage.Certain))
    )
  }

  test("ChanceTable rejects an unknown table kind") {
    val res = ConfigSource.string("t { WeightTable = [] }").at("t").load[ChanceTable[String]]
    assert(res.isLeft)
  }

  test("ChanceTable rolls each entry independently") {
    val table = ChanceTable(List(ChanceEntry("a", Percentage(0.5f)), ChanceEntry("b", Percentage(0.5f))))
    assertEquals(table.roll(() => 0.0f), List("a", "b"))
    assertEquals(table.roll(() => 0.9f), Nil)
  }

  // --- XorShiftRng (rng.rs) ---

  test("XorShiftRng produces unit floats in [0, 1)") {
    val rng = XorShiftRng(12345L)
    (1 to 1000).foreach { _ =>
      val f = rng.unitFloat()
      assert(f >= 0.0f && f < 1.0f, s"out of range: $f")
    }
  }

  test("XorShiftRng range is inclusive and bounded") {
    val rng = XorShiftRng(999L)
    (1 to 1000).foreach { _ =>
      val v = rng.rangeInt(3, 7)
      assert(v >= 3 && v <= 7, s"out of range: $v")
    }
    assertEquals(rng.rangeInt(5, 5), 5)
  }
