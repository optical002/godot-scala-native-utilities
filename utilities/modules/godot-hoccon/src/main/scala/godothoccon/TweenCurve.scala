package godothoccon

import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** A Godot tween easing curve referenced by name, ported from
  * `rust/framework/src/config/curve.rs`.
  *
  * Parsed from HOCON strings:
  *   - `"linear"`
  *   - `"ease{In|Out|InOut|OutIn}{Sine|Quad|Cubic|Quart|Quint|Expo|Circ|Elastic|Back|Bounce|Spring}"`
  *     (e.g. `"easeInBack"`, `"easeOutElastic"`).
  *
  * The Rust original delegated to `Tween::interpolate_value` (the engine's
  * static easing evaluator). The Scala-Native binding does not expose that
  * static nor the `TransitionType`/`EaseType` enums, so [[sample]] evaluates the
  * same equations directly — a port of Godot's `scene/animation/tween.cpp`
  * interpolators, so the output matches the engine for the curves games use.
  */
final case class TweenCurveConfig(trans: TransitionType, ease: EaseType):

  /** Eased progress in `[0, 1]`-ish (Back/Elastic overshoot) for `elapsed`
    * seconds into a ramp of `duration` seconds. Past the ramp (or for a
    * non-positive duration) returns the curve's end value, 1.0. */
  def sample(elapsed: Float, duration: Float): Float =
    if duration <= 0.0f || elapsed >= duration then 1.0f
    else TweenCurveConfig.runEase(trans, ease, math.max(elapsed, 0.0f).toDouble, 0.0, 1.0, duration.toDouble).toFloat

/** Godot `Tween.TransitionType`. */
enum TransitionType:
  case Linear, Sine, Quint, Quart, Quad, Expo, Elastic, Cubic, Circ, Bounce, Back, Spring

/** Godot `Tween.EaseType`. */
enum EaseType:
  case In, Out, InOut, OutIn

object TweenCurveConfig:

  given ConfigReader[TweenCurveConfig] =
    ConfigReader.fromCursor: cur =>
      cur.asString.flatMap: name =>
        parseCurveName(name) match
          case Right(c) => Right(c)
          case Left(msg) => cur.failed(CannotConvert(name, "TweenCurveConfig", msg))

  // "InOut"/"OutIn" must be tried before their prefixes "In"/"Out".
  private val Eases: List[(String, EaseType)] = List(
    "InOut" -> EaseType.InOut,
    "OutIn" -> EaseType.OutIn,
    "In"    -> EaseType.In,
    "Out"   -> EaseType.Out
  )

  private val Transitions: List[(String, TransitionType)] = List(
    "Sine"    -> TransitionType.Sine,
    "Quad"    -> TransitionType.Quad,
    "Cubic"   -> TransitionType.Cubic,
    "Quart"   -> TransitionType.Quart,
    "Quint"   -> TransitionType.Quint,
    "Expo"    -> TransitionType.Expo,
    "Circ"    -> TransitionType.Circ,
    "Elastic" -> TransitionType.Elastic,
    "Back"    -> TransitionType.Back,
    "Bounce"  -> TransitionType.Bounce,
    "Spring"  -> TransitionType.Spring
  )

  def parseCurveName(name: String): Either[String, TweenCurveConfig] =
    if name == "linear" then Right(TweenCurveConfig(TransitionType.Linear, EaseType.In))
    else if !name.startsWith("ease") then Left(invalid(name))
    else
      val rest = name.stripPrefix("ease")
      val easeMatch = Eases.collectFirst { case (p, e) if rest.startsWith(p) => (e, rest.stripPrefix(p)) }
      easeMatch match
        case None => Left(invalid(name))
        case Some((ease, transName)) =>
          Transitions.collectFirst { case (n, t) if n == transName => t } match
            case Some(trans) => Right(TweenCurveConfig(trans, ease))
            case None        => Left(invalid(name))

  private def invalid(name: String): String =
    s"""unknown curve '$name', expected "linear" or "ease{In|Out|InOut|OutIn}{Sine|Quad|Cubic|Quart|Quint|Expo|Circ|Elastic|Back|Bounce|Spring}""""

  // --- Easing equations (port of Godot scene/animation/tween.cpp) -----------
  // t: current time, b: start value, c: change, d: duration.

  private def runEase(trans: TransitionType, ease: EaseType, t: Double, b: Double, c: Double, d: Double): Double =
    ease match
      case EaseType.In    => in(trans, t, b, c, d)
      case EaseType.Out   => out(trans, t, b, c, d)
      case EaseType.InOut => inOut(trans, t, b, c, d)
      case EaseType.OutIn => outIn(trans, t, b, c, d)

  private def in(trans: TransitionType, t: Double, b: Double, c: Double, d: Double): Double =
    transIn(trans, t, b, c, d)

  private def out(trans: TransitionType, t: Double, b: Double, c: Double, d: Double): Double =
    transOut(trans, t, b, c, d)

  private def inOut(trans: TransitionType, t: Double, b: Double, c: Double, d: Double): Double =
    transInOut(trans, t, b, c, d)

  private def outIn(trans: TransitionType, t: Double, b: Double, c: Double, d: Double): Double =
    transOutIn(trans, t, b, c, d)

  // Each transition family implements in/out/in_out/out_in. Godot composes
  // out = mirror(in), in_out and out_in from the in/out pair.
  private def transIn(tr: TransitionType, t: Double, b: Double, c: Double, d: Double): Double = tr match
    case TransitionType.Linear  => Linear.in(t, b, c, d)
    case TransitionType.Sine    => Sine.in(t, b, c, d)
    case TransitionType.Quint   => power(5).in(t, b, c, d)
    case TransitionType.Quart   => power(4).in(t, b, c, d)
    case TransitionType.Quad    => power(2).in(t, b, c, d)
    case TransitionType.Cubic   => power(3).in(t, b, c, d)
    case TransitionType.Expo    => Expo.in(t, b, c, d)
    case TransitionType.Elastic => Elastic.in(t, b, c, d)
    case TransitionType.Circ    => Circ.in(t, b, c, d)
    case TransitionType.Bounce  => Bounce.in(t, b, c, d)
    case TransitionType.Back    => Back.in(t, b, c, d)
    case TransitionType.Spring  => Spring.in(t, b, c, d)

  private def transOut(tr: TransitionType, t: Double, b: Double, c: Double, d: Double): Double = tr match
    case TransitionType.Linear  => Linear.out(t, b, c, d)
    case TransitionType.Sine    => Sine.out(t, b, c, d)
    case TransitionType.Quint   => power(5).out(t, b, c, d)
    case TransitionType.Quart   => power(4).out(t, b, c, d)
    case TransitionType.Quad    => power(2).out(t, b, c, d)
    case TransitionType.Cubic   => power(3).out(t, b, c, d)
    case TransitionType.Expo    => Expo.out(t, b, c, d)
    case TransitionType.Elastic => Elastic.out(t, b, c, d)
    case TransitionType.Circ    => Circ.out(t, b, c, d)
    case TransitionType.Bounce  => Bounce.out(t, b, c, d)
    case TransitionType.Back    => Back.out(t, b, c, d)
    case TransitionType.Spring  => Spring.out(t, b, c, d)

  private def transInOut(tr: TransitionType, t: Double, b: Double, c: Double, d: Double): Double = tr match
    case TransitionType.Linear  => Linear.inOut(t, b, c, d)
    case TransitionType.Sine    => Sine.inOut(t, b, c, d)
    case TransitionType.Quint   => power(5).inOut(t, b, c, d)
    case TransitionType.Quart   => power(4).inOut(t, b, c, d)
    case TransitionType.Quad    => power(2).inOut(t, b, c, d)
    case TransitionType.Cubic   => power(3).inOut(t, b, c, d)
    case TransitionType.Expo    => Expo.inOut(t, b, c, d)
    case TransitionType.Elastic => Elastic.inOut(t, b, c, d)
    case TransitionType.Circ    => Circ.inOut(t, b, c, d)
    case TransitionType.Bounce  => Bounce.inOut(t, b, c, d)
    case TransitionType.Back    => Back.inOut(t, b, c, d)
    case TransitionType.Spring  => Spring.inOut(t, b, c, d)

  private def transOutIn(tr: TransitionType, t: Double, b: Double, c: Double, d: Double): Double = tr match
    case TransitionType.Linear  => Linear.outIn(t, b, c, d)
    case TransitionType.Sine    => Sine.outIn(t, b, c, d)
    case TransitionType.Quint   => power(5).outIn(t, b, c, d)
    case TransitionType.Quart   => power(4).outIn(t, b, c, d)
    case TransitionType.Quad    => power(2).outIn(t, b, c, d)
    case TransitionType.Cubic   => power(3).outIn(t, b, c, d)
    case TransitionType.Expo    => Expo.outIn(t, b, c, d)
    case TransitionType.Elastic => Elastic.outIn(t, b, c, d)
    case TransitionType.Circ    => Circ.outIn(t, b, c, d)
    case TransitionType.Bounce  => Bounce.outIn(t, b, c, d)
    case TransitionType.Back    => Back.outIn(t, b, c, d)
    case TransitionType.Spring  => Spring.outIn(t, b, c, d)

  /** Family of in/out/in_out/out_in equations for one transition. */
  private trait Family:
    def in(t: Double, b: Double, c: Double, d: Double): Double
    def out(t: Double, b: Double, c: Double, d: Double): Double
    def inOut(t: Double, b: Double, c: Double, d: Double): Double
    def outIn(t: Double, b: Double, c: Double, d: Double): Double

  private object Linear extends Family:
    def in(t: Double, b: Double, c: Double, d: Double): Double = c * t / d + b
    def out(t: Double, b: Double, c: Double, d: Double): Double = c * t / d + b
    def inOut(t: Double, b: Double, c: Double, d: Double): Double = c * t / d + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double = c * t / d + b

  private object Sine extends Family:
    def in(t: Double, b: Double, c: Double, d: Double): Double =
      -c * math.cos(t / d * (math.Pi / 2)) + c + b
    def out(t: Double, b: Double, c: Double, d: Double): Double =
      c * math.sin(t / d * (math.Pi / 2)) + b
    def inOut(t: Double, b: Double, c: Double, d: Double): Double =
      -c / 2 * (math.cos(math.Pi * t / d) - 1) + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  /** Quad/Cubic/Quart/Quint share a power-`n` shape (Penner forms; for even `n`
    * the `out`/second-`inOut` half flips sign and offset, for odd `n` it does
    * not — matching Godot's quad/cubic/quart/quint). */
  private def power(n: Int): Family = new Family:
    private val even = n % 2 == 0
    def in(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d
      c * math.pow(t, n) + b
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d - 1
      if even then -c * (math.pow(t, n) - 1) + b
      else c * (math.pow(t, n) + 1) + b
    def inOut(t0: Double, b: Double, c: Double, d: Double): Double =
      var t = t0 / (d / 2)
      if t < 1 then c / 2 * math.pow(t, n) + b
      else
        t -= 2
        if even then -c / 2 * (math.pow(t, n) - 2) + b
        else c / 2 * (math.pow(t, n) + 2) + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Expo extends Family:
    def in(t: Double, b: Double, c: Double, d: Double): Double =
      if t == 0 then b else c * math.pow(2, 10 * (t / d - 1)) + b - c * 0.001
    def out(t: Double, b: Double, c: Double, d: Double): Double =
      if t == d then b + c else c * (-math.pow(2, -10 * t / d) + 1) + b
    def inOut(t0: Double, b: Double, c: Double, d: Double): Double =
      if t0 == 0 then b
      else if t0 == d then b + c
      else
        var t = t0 / (d / 2)
        if t < 1 then c / 2 * math.pow(2, 10 * (t - 1)) + b - c * 0.0005
        else
          t -= 1
          c / 2 * (-math.pow(2, -10 * t) + 2) + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Circ extends Family:
    def in(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d
      -c * (math.sqrt(1 - t * t) - 1) + b
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d - 1
      c * math.sqrt(1 - t * t) + b
    def inOut(t0: Double, b: Double, c: Double, d: Double): Double =
      var t = t0 / (d / 2)
      if t < 1 then -c / 2 * (math.sqrt(1 - t * t) - 1) + b
      else
        t -= 2
        c / 2 * (math.sqrt(1 - t * t) + 1) + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Elastic extends Family:
    def in(t0: Double, b: Double, c: Double, d: Double): Double =
      if t0 == 0 then b
      else
        var t = t0 / d
        if t == 1 then b + c
        else
          val p = d * 0.3
          val a = c
          val s = p / 4
          t -= 1
          -(a * math.pow(2, 10 * t) * math.sin((t * d - s) * (2 * math.Pi) / p)) + b
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      if t0 == 0 then b
      else
        val t = t0 / d
        if t == 1 then b + c
        else
          val p = d * 0.3
          val a = c
          val s = p / 4
          a * math.pow(2, -10 * t) * math.sin((t * d - s) * (2 * math.Pi) / p) + c + b
    def inOut(t0: Double, b: Double, c: Double, d: Double): Double =
      if t0 == 0 then b
      else
        var t = t0 / (d / 2)
        if t == 2 then b + c
        else
          val p = d * (0.3 * 1.5)
          val a = c
          val s = p / 4
          if t < 1 then
            t -= 1
            -0.5 * (a * math.pow(2, 10 * t) * math.sin((t * d - s) * (2 * math.Pi) / p)) + b
          else
            t -= 1
            a * math.pow(2, -10 * t) * math.sin((t * d - s) * (2 * math.Pi) / p) * 0.5 + c + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Back extends Family:
    private val s = 1.70158
    def in(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d
      c * t * t * ((s + 1) * t - s) + b
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d - 1
      c * (t * t * ((s + 1) * t + s) + 1) + b
    def inOut(t0: Double, b: Double, c: Double, d: Double): Double =
      val s2 = s * 1.525
      var t = t0 / (d / 2)
      if t < 1 then c / 2 * (t * t * ((s2 + 1) * t - s2)) + b
      else
        t -= 2
        c / 2 * (t * t * ((s2 + 1) * t + s2) + 2) + b
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Bounce extends Family:
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      var t = t0 / d
      if t < (1 / 2.75) then c * (7.5625 * t * t) + b
      else if t < (2 / 2.75) then
        t -= 1.5 / 2.75
        c * (7.5625 * t * t + 0.75) + b
      else if t < (2.5 / 2.75) then
        t -= 2.25 / 2.75
        c * (7.5625 * t * t + 0.9375) + b
      else
        t -= 2.625 / 2.75
        c * (7.5625 * t * t + 0.984375) + b
    def in(t: Double, b: Double, c: Double, d: Double): Double =
      c - out(d - t, 0, c, d) + b
    def inOut(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then in(t * 2, b, c / 2, d)
      else out(t * 2 - d, b + c / 2, c / 2, d)
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)

  private object Spring extends Family:
    def out(t0: Double, b: Double, c: Double, d: Double): Double =
      val t = t0 / d
      val s = 1.0 - t
      val r = (math.sin(t * math.Pi * (0.2 + 2.5 * t * t * t)) * math.pow(s, 2.2) + t) *
        (1.0 + (1.2 * s))
      c * r + b
    def in(t: Double, b: Double, c: Double, d: Double): Double =
      c - out(d - t, 0, c, d) + b
    def inOut(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then in(t * 2, b, c / 2, d)
      else out(t * 2 - d, b + c / 2, c / 2, d)
    def outIn(t: Double, b: Double, c: Double, d: Double): Double =
      if t < d / 2 then out(t * 2, b, c / 2, d) else in(t * 2 - d, b + c / 2, c / 2, d)
