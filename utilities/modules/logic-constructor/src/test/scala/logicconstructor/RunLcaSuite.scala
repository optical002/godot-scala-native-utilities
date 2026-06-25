package logicconstructor

import TestFixtures.*
import TestFixtures.LcGameEntity.*

/** Port of `tests/basic_usage.rs::running_predefined_actions` plus focused
  * collision-arm checks for [[runLca]].
  */
class RunLcaSuite extends munit.FunSuite:

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

    // OTHER attack: player→enemy, different kinds ⇒ enemy takes 15.
    runLca(attack, player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 85.0)

    // OTHER heal twice: enemy +10 total.
    runLca(heal, player, enemy)
    runLca(heal, player, enemy)
    assertEquals(playerHp.value, 100.0)
    assertEquals(enemyHp.value, 95.0)

    // SELF heal: applies source→source, player +5.
    runLca(selfHeal, player, enemy)
    assertEquals(playerHp.value, 105.0)
    assertEquals(enemyHp.value, 95.0)

    // SAME_KIND attack with player vs player ⇒ player -10.
    runLca(friendlyAttack, player, player)
    assertEquals(playerHp.value, 95.0)
    assertEquals(enemyHp.value, 95.0)

    // OTHER attack with player vs player ⇒ same kind, so nothing fires.
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

    // Different kinds: Self arm hits source, Other arm hits target.
    runLca(cfg, entity(Player(playerHp)), entity(Enemy(enemyHp)))
    assertEquals(playerHp.value, 90.0)
    assertEquals(enemyHp.value, 90.0)

    // Same kind: only Self arm fires (Other is suppressed).
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
    runLca(LcActionConfig.dummy[LcGameEntity], entity(Player(hp)), entity(Enemy(Health(100.0))))
    assertEquals(hp.value, 100.0)
  }
