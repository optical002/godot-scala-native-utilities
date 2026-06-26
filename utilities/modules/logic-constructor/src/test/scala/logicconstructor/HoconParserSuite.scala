package logicconstructor

import pureconfig.ConfigSource

import TestFixtures.*
import logicconstructor.LcAction.Config
import logicconstructor.parser.*

/** End-to-end: real multi-line HOCON documents -> pureconfig -> typed config -> running actions. */
class HoconParserSuite extends munit.FunSuite:

  test("HOCON full-form single config runs end to end") {
    val cfg = ConfigSource
      .string("""
        action {
          lca { DealDamage = 15 }
          collision = "Self"
        }
      """)
      .at("action")
      .load[Config.Single[LcGameEntity]]
      .toOption
      .get

    assertEquals(cfg.collision, CollisionKind.Self)

    val hp = TestFixtures.Health(100.0)
    val src = entity(LcGameEntity.Enemy(hp))
    Config(Vector(cfg)).runLca(src, src)
    assertEquals(hp.value, 85.0)
  }

  test("HOCON array of actions runs end to end") {
    // HOCON documents are rooted at an object, so the action list lives under a key.
    val action = parseActions[LcGameEntity](
      "actions",
      """
        actions = [
          { DealDamage = 15 },
          { lca { Heal = 4 }, collision = "Self" }
        ]
      """
    ).toOption.get
    assertEquals(action.data.size, 2)

    val playerHp = TestFixtures.Health(100.0)
    val enemyHp = TestFixtures.Health(100.0)
    action.runLca(entity(LcGameEntity.Player(playerHp)), entity(LcGameEntity.Enemy(enemyHp)))
    assertEquals(enemyHp.value, 85.0)
    assertEquals(playerHp.value, 104.0)
  }

  test("invalid HOCON returns a Left") {
    assert(parseActions[LcGameEntity]("actions", "{ this is = = broken").isLeft)
  }

  test("unknown effect surfaces an error") {
    val err = parseActions[LcGameEntity]("actions", """actions = [ { Unknown = 5 } ]""").left.toOption.get
    assert(err.contains("unknown effect"), err)
  }
