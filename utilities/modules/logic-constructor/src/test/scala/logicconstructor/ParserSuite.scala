package logicconstructor

import pureconfig.{ConfigReader, ConfigSource}

import TestFixtures.*
import logicconstructor.LcAction.Config
import logicconstructor.parser.*

/** Decoding tests: HOCON text -> pureconfig `ConfigReader`s -> typed `LcAction.Config`.
  *
  * All decoding is derivation/instance-driven (no hand-written parsers), so these exercise the
  * `CollisionKind`, `LcAction.Config.Single` and `LcAction.Config` readers directly.
  */
class ParserSuite extends munit.FunSuite:

  // Decode a `CollisionKind` straight from a HOCON string value.
  private def collision(s: String): Either[String, CollisionKind] =
    ConfigSource.string(s"""x = "$s"""").at("x").load[CollisionKind].left.map(_.prettyPrint())

  // Decode a single action from a HOCON object literal.
  private def single(hocon: String): Either[String, Config.Single[LcGameEntity]] =
    ConfigSource.string(s"x = $hocon").at("x").load[Config.Single[LcGameEntity]].left.map(_.prettyPrint())

  // Decode an action list from a HOCON array literal.
  private def list(hocon: String): Either[String, Config[LcGameEntity]] =
    parseActions[LcGameEntity]("x", s"x = $hocon")

  test("parse single collision kinds") {
    assertEquals(collision("Self"), Right(CollisionKind.Self))
    assertEquals(collision("SameKind"), Right(CollisionKind.SameKind))
    assertEquals(collision("Other"), Right(CollisionKind.Other))
  }

  test("parse piped collision kinds") {
    val two = collision("Self | Other")
    assert(two.exists(_.contains(CollisionKind.Self)))
    assert(two.exists(_.contains(CollisionKind.Other)))
    assert(two.exists(k => !k.contains(CollisionKind.SameKind)))

    val noSpaces = collision("Self|SameKind")
    assert(noSpaces.exists(_.contains(CollisionKind.Self)))
    assert(noSpaces.exists(_.contains(CollisionKind.SameKind)))

    val all = collision("Self | SameKind | Other")
    assert(all.exists(_.contains(CollisionKind.Self)))
    assert(all.exists(_.contains(CollisionKind.SameKind)))
    assert(all.exists(_.contains(CollisionKind.Other)))
  }

  test("parse unknown collision kind errors") {
    assert(collision("UnknownKind").left.exists(_.contains("Unknown CollisionKind")))
  }

  test("simple format defaults to Other") {
    val cfg = single("{ DealDamage = 10 }").toOption.get
    assertEquals(cfg.collision, CollisionKind.Other)
    assertEquals(cfg.action, DealDamage(10.0))
  }

  test("full format parses collision") {
    val cfg = single("""{ lca { DealDamage = 25 }, collision = "Self" }""").toOption.get
    assertEquals(cfg.collision, CollisionKind.Self)
    assertEquals(cfg.action, DealDamage(25.0))
  }

  test("non-object single is an error") {
    assert(single("42").isLeft)
  }

  test("typed config via effect reader runs") {
    val cfg = single("""{ lca { DealDamage = 15 }, collision = "Self" }""").toOption.get
    assertEquals(cfg.collision, CollisionKind.Self)

    val hp = TestFixtures.Health(100.0)
    val src = entity(LcGameEntity.Enemy(hp))
    Config(Vector(cfg)).runLca(src, src)
    assertEquals(hp.value, 85.0)
  }

  test("empty array parses to empty list") {
    assertEquals(list("[]").map(_.data.size), Right(0))
  }

  test("list mixes simple and full forms") {
    val configs = list("""[
      { DealDamage = 10 },
      { lca { Heal = 20 }, collision = "Self" },
      { lca { DealDamage = 30 }, collision = "Self | Other" }
    ]""").toOption.get.data
    assertEquals(configs.size, 3)
    assertEquals(configs(0).collision, CollisionKind.Other)
    assertEquals(configs(1).collision, CollisionKind.Self)
    assertEquals(configs(2).collision, CollisionKind.Self | CollisionKind.Other)
  }

  test("list errors carry the inner message") {
    val err = list("""[ { DealDamage = 10 }, { Unknown = 5 } ]""").left.toOption.get
    assert(err.contains("unknown effect"), err)
  }

  test("list rejects a non-array") {
    assert(list("""{ DealDamage = 10 }""").isLeft)
  }

  test("parsed action config runs end to end") {
    val action = list("""[
      { DealDamage = 15 },
      { lca { Heal = 4 }, collision = "Self" }
    ]""").toOption.get
    assertEquals(action.data.size, 2)

    val playerHp = TestFixtures.Health(100.0)
    val enemyHp = TestFixtures.Health(100.0)
    action.runLca(entity(LcGameEntity.Player(playerHp)), entity(LcGameEntity.Enemy(enemyHp)))
    assertEquals(enemyHp.value, 85.0)
    assertEquals(playerHp.value, 104.0)
  }
