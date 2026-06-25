package logicconstructor

/** A minimal config value tree — the parser surface of this library.
  *
  * The Rust crate threaded `hocon-rs`'s `Value` through every parser. No HOCON
  * parser publishes a Scala-Native-0.5 + Scala-3 artifact, so this library
  * carries its own tiny ADT instead. It mirrors only the shapes the parsers
  * actually touch — strings, numbers, objects, arrays — not the full HOCON
  * grammar. A consumer produces a `ConfigValue` however it likes (from a Godot
  * `ConfigFile`/JSON read, a hand-written reader, or the literals below in
  * tests) and feeds it to [[parseLcActionConfig]] and friends.
  *
  * Object key order is preserved ([[CObj]] wraps an ordered `Seq`) so the
  * "single key" effect form parses deterministically, matching the Rust port's
  * use of ordered iteration.
  */
enum ConfigValue:
  case CStr(value: String)
  case CNum(value: Double)
  case CBool(value: Boolean)
  case CObj(fields: Seq[(String, ConfigValue)])
  case CArr(items: Seq[ConfigValue])

object ConfigValue:
  /** Convenience builder for an object from key/value pairs. */
  def obj(fields: (String, ConfigValue)*): ConfigValue = CObj(fields.toVector)

  /** Convenience builder for an array. */
  def arr(items: ConfigValue*): ConfigValue = CArr(items.toVector)

  extension (self: ConfigValue)
    /** The fields if this is an object, else `None`. */
    def asObject: Option[Seq[(String, ConfigValue)]] = self match
      case CObj(fields) => Some(fields)
      case _            => None

    /** The items if this is an array, else `None`. */
    def asArray: Option[Seq[ConfigValue]] = self match
      case CArr(items) => Some(items)
      case _           => None

    /** The string if this is a string, else `None`. */
    def asString: Option[String] = self match
      case CStr(s) => Some(s)
      case _       => None

    /** The number if this is numeric, else `None`. */
    def asNumber: Option[Double] = self match
      case CNum(n) => Some(n)
      case _       => None

    /** Look up a field by key in an object (first match wins). */
    def get(key: String): Option[ConfigValue] = self match
      case CObj(fields) => fields.collectFirst { case (k, v) if k == key => v }
      case _            => None

    /** Whether an object contains the given key. */
    def contains(key: String): Boolean = get(key).isDefined
