package logicconstructor

enum ConfigValue:
  case CStr(value: String)
  case CNum(value: Double)
  case CBool(value: Boolean)
  case CObj(fields: Seq[(String, ConfigValue)])
  case CArr(items: Seq[ConfigValue])

object ConfigValue:

  def obj(fields: (String, ConfigValue)*): ConfigValue = CObj(fields.toVector)

  def arr(items: ConfigValue*): ConfigValue = CArr(items.toVector)

  extension (self: ConfigValue)

    def asObject: Option[Seq[(String, ConfigValue)]] = self match
      case CObj(fields) => Some(fields)
      case _            => None

    def asArray: Option[Seq[ConfigValue]] = self match
      case CArr(items) => Some(items)
      case _           => None

    def asString: Option[String] = self match
      case CStr(s) => Some(s)
      case _       => None

    def asNumber: Option[Double] = self match
      case CNum(n) => Some(n)
      case _       => None

    def get(key: String): Option[ConfigValue] = self match
      case CObj(fields) => fields.collectFirst { case (k, v) if k == key => v }
      case _            => None

    def contains(key: String): Boolean = get(key).isDefined
