package logicconstructor

import TestFixtures.*
import TestFixtures.LcGameEntity.*

class RunLcaSuite extends munit.FunSuite:

  // The test actions ignore the ctx, so a `Unit` ctx suffices.
  given Unit = ()

  test("predefined actions fire by collision kind") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)
    def player = entity(Player(playerHp))
    def enemy = entity(Enemy(enemyHp))

    val attack = LcActionConfig(
      Vector(LcSingleActionConfig(DealDamage(15.0), CollisionKind.Other))
    )
    val friendlyAttack = LcActionConfig(
      Vector(LcSingleActionConfig(DealDamage(10.0), CollisionKind.SameKind))
    )
    val heal = LcActionConfig(
      Vector(LcSingleActionConfig(Heal(5.0), CollisionKind.Other))
    )
    val selfHeal = LcActionConfig(
      Vector(LcSingleActionConfig(Heal(5.0), CollisionKind.Self))
    )

    runLca(attack, player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 85.0)

    runLca(heal, player, enemy)
    runLca(heal, player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 95.0)

    runLca(selfHeal, player, enemy)
    assertEquals(playerHp.value, 105.0)
    assertEquals(enemyHp.value, 95.0)

    runLca(friendlyAttack, player, player)
    assertEquals(playerHp.value, 95.0)
    assertEquals(enemyHp.value, 95.0)

    runLca(attack, player, player)
    assertEquals(playerHp.value, 95.0)
    assertEquals(enemyHp.value, 95.0)
  }

  test("Self | Other fires both arms appropriately") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)

    val cfg = LcActionConfig(
      Vector(
        LcSingleActionConfig(
          DealDamage(10.0),
          CollisionKind.Self | CollisionKind.Other
        )
      )
    )

    runLca(cfg, entity(Player(playerHp)), entity(Enemy(enemyHp)))
    assertEquals(playerHp.value, 90.0)
    assertEquals(enemyHp.value, 90.0)

    val pHp = Health(100.0)
    val p2Hp = Health(100.0)
    runLca(cfg, entity(Player(pHp)), entity(Player(p2Hp)))
    assertEquals(pHp.value, 90.0)
    assertEquals(p2Hp.value, 100.0)
  }

  test("LcSourceWithAction.run binds source") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)
    val swa = LcSourceWithAction(
      entity(Player(playerHp)),
      LcActionConfig(Vector(LcSingleActionConfig(DealDamage(7.0), CollisionKind.Other)))
    )
    swa.run(entity(Enemy(enemyHp)))
    assertEquals(enemyHp.value, 93.0)
  }

  test("empty / dummy config does nothing") {
    val hp = Health(100.0)
    runLca(LcActionConfig.dummy[LcGameEntity, Unit], entity(Player(hp)), entity(Enemy(Health(100.0))))
    assertEquals(hp.value, 100.0)
  }
