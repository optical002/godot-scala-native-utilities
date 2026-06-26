package logicconstructor

import TestFixtures.*
import TestFixtures.LcGameEntity.*
import logicconstructor.LcAction.Config
import logicconstructor.LcAction.Config.{Single, WithSource}

class RunLcaSuite extends munit.FunSuite:

  test("predefined actions fire by collision kind") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)
    def player = Player(playerHp)
    def enemy = Enemy(enemyHp)

    val attack = Config(Vector(Single(DealDamage(15.0), CollisionKind.Other)))
    val friendlyAttack = Config(Vector(Single(DealDamage(10.0), CollisionKind.SameKind)))
    val heal = Config(Vector(Single(Heal(5.0), CollisionKind.Other)))
    val selfHeal = Config(Vector(Single(Heal(5.0), CollisionKind.Self)))

    attack.runLca(player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 85.0)

    heal.runLca(player, enemy)
    heal.runLca(player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 95.0)

    selfHeal.runLca(player, enemy)
    assertEquals(playerHp.value, 105.0)
    assertEquals(enemyHp.value, 95.0)

    friendlyAttack.runLca(player, player)
    assertEquals(playerHp.value, 95.0)
    assertEquals(enemyHp.value, 95.0)

    attack.runLca(player, player)
    assertEquals(playerHp.value, 95.0)
    assertEquals(enemyHp.value, 95.0)
  }

  test("Self | Other fires both arms appropriately") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)

    val cfg = Config(
      Vector(Single(DealDamage(10.0), CollisionKind.Self | CollisionKind.Other))
    )

    cfg.runLca(Player(playerHp), Enemy(enemyHp))
    assertEquals(playerHp.value, 90.0)
    assertEquals(enemyHp.value, 90.0)

    val pHp = Health(100.0)
    val p2Hp = Health(100.0)
    cfg.runLca(Player(pHp), Player(p2Hp))
    assertEquals(pHp.value, 90.0)
    assertEquals(p2Hp.value, 100.0)
  }

  test("WithSource.run binds source") {
    val playerHp = Health(100.0)
    val enemyHp = Health(100.0)
    val swa = WithSource(
      Config(Vector(Single(DealDamage(7.0), CollisionKind.Other))),
      Player(playerHp)
    )
    swa.run(Enemy(enemyHp))
    assertEquals(enemyHp.value, 93.0)
  }

  test("empty / dummy config does nothing") {
    val hp = Health(100.0)
    Config.dummy[LcGameEntity].runLca(Player(hp), Enemy(Health(100.0)))
    assertEquals(hp.value, 100.0)
  }
