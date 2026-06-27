package godothoccon

import gdext.builtin.Color
import pureconfig.ConfigReader
import pureconfig.error.CannotConvert

/** A [[ConfigReader]] for Godot's [[gdext.builtin.Color]], parsed from an HTML
  * hex string: `"#ffffff"`, `"#ffd23f"`, `"#80ffffff"` (with leading alpha), or
  * the same without the leading `#`. Three- and four-digit shorthand
  * (`"#fff"`, `"#ffff"`) is also accepted, matching Godot's `Color.from_html`.
  *
  * The Rust original used `Color::from_html`; the Scala-Native binding's `Color`
  * is a plain `(r, g, b, a)` case class with no HTML constructor, so the parsing
  * is re-implemented here.
  *
  * Ported from `framework/src/config/color.rs`.
  */
object ColorConfig:

  /** Parse an HTML hex color into channel floats in `[0, 1]`. Mirrors Godot's
    * `Color.from_html`: an 8/4-digit form is `AARRGGBB` (alpha first); the
    * 6/3-digit form is opaque.
    */
  def fromHtml(raw: String): Either[String, Color] =
    val s = raw.trim.stripPrefix("#")
    def hex2(str: String): Option[Float] =
      try Some(Integer.parseInt(str, 16) / 255.0f)
      catch case _: NumberFormatException => None
    val expanded = s.length match
      case 3 | 4 => Some(s.map(c => s"$c$c").mkString) // "f" -> "ff"
      case 6 | 8 => Some(s)
      case _ => None
    expanded match
      case None =>
        Left(s"'$raw' is not a valid HTML hex color (e.g. \"#ffffff\", \"#ffd23f\")")
      case Some(hex) =>
        val (a, rgb) = if hex.length == 8 then (hex.substring(0, 2), hex.substring(2)) else ("ff", hex)
        val channels =
          for
            r <- hex2(rgb.substring(0, 2))
            g <- hex2(rgb.substring(2, 4))
            b <- hex2(rgb.substring(4, 6))
            av <- hex2(a)
          yield Color(r, g, b, av)
        channels.toRight(s"'$raw' is not a valid HTML hex color (e.g. \"#ffffff\", \"#ffd23f\")")

  given ConfigReader[Color] = ConfigReader.fromCursor: cur =>
    cur.asString.flatMap: s =>
      fromHtml(s) match
        case Right(c) => Right(c)
        case Left(msg) => cur.failed(CannotConvert(s, "Color", msg))
