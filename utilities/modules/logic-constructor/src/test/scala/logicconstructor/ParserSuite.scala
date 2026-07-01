package logicconstructor

import TestFixtures.*
import logicconstructor.ConfigValue.*
import logicconstructor.parser.*

class ParserSuite extends munit.FunSuite:

  given Unit = ()

  test("parse single collision kinds") {
    assertEquals(parseCollisionKind(CStr("Self")), Right(CollisionKind.Self))
    assertEquals(parseCollisionKind(CStr("SameKind")), Right(CollisionKind.SameKind))
    assertEquals(parseCollisionKind(CStr("Other")), Right(CollisionKind.Other))
  }

  test("parse piped collision kinds") {
    val two = parseCollisionKind(CStr("Self | Other"))
    assert(two.exists(_.contains(CollisionKind.Self)))
    assert(two.exists(_.contains(CollisionKind.Other)))
    assert(two.exists(k => !k.contains(CollisionKind.SameKind)))

    val noSpaces = parseCollisionKind(CStr("Self|SameKind"))
    assert(noSpaces.exists(_.contains(CollisionKind.Self)))
    assert(noSpaces.exists(_.contains(CollisionKind.SameKind)))

    val all = parseCollisionKind(CStr("Self | SameKind | Other"))
    assert(all.exists(_.contains(CollisionKind.Self)))
    assert(all.exists(_.contains(CollisionKind.SameKind)))
    assert(all.exists(_.contains(CollisionKind.Other)))
  }

  test("parse unknown collision kind errors") {
    val err = parseCollisionKind(CStr("UnknownKind"))
    assert(err.left.exists(_.contains("Unknown CollisionKind")))
  }

  test("parse non-string collision kind errors") {
    val err = parseCollisionKind(CNum(123))
    assert(err.left.exists(_.contains("expects a string")))
  }

  test("simple format raw defaults to Other") {
    val value = obj("DealDamage" -> CNum(10))
    val raw = parseLcConfigRaw(value).toOption.get
    assertEquals(raw.collision, CollisionKind.Other)
    assert(raw.effectValue.contains("DealDamage"))
  }

  test("full format raw parses collision") {
    val value = obj(
      "lca" -> obj("DealDamage" -> CNum(25)),
      "collision" -> CStr("Self")
    )
    val raw = parseLcConfigRaw(value).toOption.get
    assertEquals(raw.collision, CollisionKind.Self)
    assert(raw.effectValue.contains("DealDamage"))
  }

  test("partial full format is an error") {
    val missingCollision = obj("lca" -> obj("DealDamage" -> CNum(10)))
    assert(parseLcConfigRaw(missingCollision).left.exists(_.contains("requires 'collision' field")))

    val missingLca = obj("collision" -> CStr("Self"))
    assert(parseLcConfigRaw(missingLca).left.exists(_.contains("requires 'lca' field")))
  }

  test("non-object raw is an error") {
    assert(parseLcConfigRaw(CNum(42)).left.exists(_.contains("expects an object")))
  }

  test("typed config via effect closure runs") {
    val value = obj("lca" -> obj("DealDamage" -> CNum(15)), "collision" -> CStr("Self"))
    val config = parseLcConfig(value, parseGameEffect).toOption.get
    assertEquals(config.collision, CollisionKind.Self)

    val hp = TestFixtures.Health(100.0)
    val src = entity(LcGameEntity.Enemy(hp))
    runLca(LcActionConfig(Vector(config)), src, src)
    assertEquals(hp.value, 85.0)
  }

  test("empty array parses to empty list") {
    assertEquals(parseLcConfigListRaw(arr()).map(_.size), Right(0))
  }

  test("list mixes simple and full forms") {
    val value = arr(
      obj("DealDamage" -> CNum(10)),
      obj("lca" -> obj("Heal" -> CNum(20)), "collision" -> CStr("Self")),
      obj("lca" -> obj("DealDamage" -> CNum(30)), "collision" -> CStr("Self | Other"))
    )
    val configs = parseLcConfigList(value, parseGameEffect).toOption.get
    assertEquals(configs.size, 3)
    assertEquals(configs(0).collision, CollisionKind.Other)
    assertEquals(configs(1).collision, CollisionKind.Self)
    assertEquals(configs(2).collision, CollisionKind.Self | CollisionKind.Other)
  }

  test("list errors carry the index and inner message") {
    val value = arr(obj("DealDamage" -> CNum(10)), obj("Unknown" -> CNum(5)))
    val err = parseLcConfigList(value, parseGameEffect).left.toOption.get
    assert(err.contains("index 1"))
    assert(err.contains("unknown effect"))
  }

  test("list rejects a non-array") {
    val err = parseLcConfigListRaw(obj("DealDamage" -> CNum(10))).left.toOption.get
    assert(err.contains("expects an array"))
  }

  test("parseLcActionConfig builds a typed config") {
    val value = arr(
      obj("DealDamage" -> CNum(10)),
      obj("lca" -> obj("Heal" -> CNum(7)), "collision" -> CStr("Self"))
    )
    val action = parseLcActionConfig(value, parseGameEffect).toOption.get
    assertEquals(action.data.size, 2)
    assertEquals(action.data(0).collision, CollisionKind.Other)
    assertEquals(action.data(1).collision, CollisionKind.Self)
  }

  test("parsed action config runs end to end") {
    val value = arr(
      obj("DealDamage" -> CNum(15)),
      obj("lca" -> obj("Heal" -> CNum(4)), "collision" -> CStr("Self"))
    )
    val action = parseLcActionConfig(value, parseGameEffect).toOption.get

    val playerHp = TestFixtures.Health(100.0)
    val enemyHp = TestFixtures.Health(100.0)
    runLca(action, entity(LcGameEntity.Player(playerHp)), entity(LcGameEntity.Enemy(enemyHp)))
    assertEquals(enemyHp.value, 85.0)
    assertEquals(playerHp.value, 104.0)
  }

  test("parseLcActionConfig rejects a non-array") {
    val err = parseLcActionConfig(obj("DealDamage" -> CNum(10)), parseGameEffect).left.toOption.get
    assert(err.contains("expects an array"))
  }

  test("parseLcActionConfig propagates inner errors with index") {
    val value = arr(obj("DealDamage" -> CNum(10)), obj("Unknown" -> CNum(1)))
    val err = parseLcActionConfig(value, parseGameEffect).left.toOption.get
    assert(err.contains("index 1"))
    assert(err.contains("unknown effect"))
  }
