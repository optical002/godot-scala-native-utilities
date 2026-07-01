package godothoccon

import pureconfig.ConfigSource

/** Coverage for the ported Godot tween-curve config: name parsing + a couple of
  * easing-equation anchors (a curve always starts at 0 and ends at 1). */
class TweenCurveSuite extends munit.FunSuite:

  private def load(body: String): Either[String, TweenCurveConfig] =
    ConfigSource.string(s"x = $body").at("x").load[TweenCurveConfig].left.map(_.prettyPrint())

  test("parses linear") {
    assertEquals(load("\"linear\""), Right(TweenCurveConfig(TransitionType.Linear, EaseType.In)))
  }

  test("parses ease{Ease}{Transition} names") {
    assertEquals(load("\"easeInBack\""), Right(TweenCurveConfig(TransitionType.Back, EaseType.In)))
    assertEquals(load("\"easeOutElastic\""), Right(TweenCurveConfig(TransitionType.Elastic, EaseType.Out)))
    assertEquals(load("\"easeInOutQuad\""), Right(TweenCurveConfig(TransitionType.Quad, EaseType.InOut)))
    assertEquals(load("\"easeOutInBounce\""), Right(TweenCurveConfig(TransitionType.Bounce, EaseType.OutIn)))
  }

  test("rejects unknown curve names") {
    assert(load("\"wobble\"").isLeft)
    assert(load("\"easeInNope\"").isLeft)
  }

  test("sample starts near 0 and reaches 1 at the end of the ramp") {
    val c = TweenCurveConfig(TransitionType.Cubic, EaseType.InOut)
    assertEqualsFloat(c.sample(0.0f, 2.0f), 0.0f, 1e-4f)
    assertEqualsFloat(c.sample(2.0f, 2.0f), 1.0f, 1e-4f)
    // Past the ramp clamps to the end value.
    assertEqualsFloat(c.sample(5.0f, 2.0f), 1.0f, 1e-6f)
    // Non-positive duration is the end value.
    assertEqualsFloat(c.sample(0.0f, 0.0f), 1.0f, 1e-6f)
  }
