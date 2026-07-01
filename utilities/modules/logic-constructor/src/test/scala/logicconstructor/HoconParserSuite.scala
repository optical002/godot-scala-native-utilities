package logicconstructor

import TestFixtures.*
import logicconstructor.ConfigValue.*
import logicconstructor.parser.*

/** End-to-end: real HOCON text -> ConfigValue (via the Scala-Native SHocon port)
  * -> logic-constructor parsers -> running actions.
  */
class HoconParserSuite extends munit.FunSuite:

  given Unit = ()

  test("parse HOCON scalars into ConfigValue") {
    assertEquals(parseHocon("""x = 10"""), Right(obj("x" -> CNum(10))))
    assertEquals(parseHocon("""x = true"""), Right(obj("x" -> CBool(true))))
    assertEquals(parseHocon("""x = hello"""), Right(obj("x" -> CStr("hello"))))
    assertEquals(parseHocon("""x = "quoted""""), Right(obj("x" -> CStr("quoted"))))
  }

  test("parse a nested HOCON object") {
    val parsed = parseHocon("""
      lca { DealDamage = 25 }
      collision = Self
    """).toOption.get
    assert(parsed.get("collision").contains(CStr("Self")))
    assert(parsed.get("lca").flatMap(_.get("DealDamage")).contains(CNum(25)))
  }

  test("HOCON full-form config runs end to end") {
    val parsed = parseHocon("""
      lca { DealDamage = 15 }
      collision = Self
    """).toOption.get

    val config = parseLcConfig(parsed, parseGameEffect).toOption.get
    assertEquals(config.collision, CollisionKind.Self)

    val hp = TestFixtures.Health(100.0)
    val src = entity(LcGameEntity.Enemy(hp))
    runLca(LcActionConfig(Vector(config)), src, src)
    assertEquals(hp.value, 85.0)
  }

  test("HOCON array of actions runs end to end") {
    // HOCON documents are rooted at an object, so the action list lives under a key.
    val parsed = parseHocon("""
      actions = [
        { DealDamage = 15 },
        { lca { Heal = 4 }, collision = Self }
      ]
    """).toOption.get
    val actionsValue = parsed.get("actions").get

    val action = parseLcActionConfig(actionsValue, parseGameEffect).toOption.get
    assertEquals(action.data.size, 2)

    val playerHp = TestFixtures.Health(100.0)
    val enemyHp = TestFixtures.Health(100.0)
    runLca(action, entity(LcGameEntity.Player(playerHp)), entity(LcGameEntity.Enemy(enemyHp)))
    assertEquals(enemyHp.value, 85.0)
    assertEquals(playerHp.value, 104.0)
  }

  test("invalid HOCON returns a Left") {
    assert(parseHocon("{ this is = = broken").isLeft)
  }
