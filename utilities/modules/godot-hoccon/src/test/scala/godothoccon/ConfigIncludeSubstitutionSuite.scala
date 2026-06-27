package godothoccon

import java.io.File

import pureconfig.ConfigReader
import pureconfig.ConfigSource

/** End-to-end check that the SHocon-backed pureconfig fork resolves HOCON
  * `include` directives AND `${...}` substitutions for the real repo `config/`
  * tree — the same path `game.config.ConfigLoader` now relies on (it just points
  * pureconfig at `application.conf` and decodes; no manual include scanning).
  *
  * `config/application.conf` pulls in `game/max_health.conf`, `game/player.conf`,
  * `game/enemy.conf` and `game/spawner.conf` via `include required(...)`.
  * `player.conf`/`enemy.conf` set `max-health: ${max-health.player|enemy}`, so a
  * correct decode proves includes are merged and substitutions resolve across the
  * merged tree.
  */
class ConfigIncludeSubstitutionSuite extends munit.FunSuite:

  private final case class PlayerCfg(movementSpeed: Double, maxHealth: Double, autoAim: Boolean)
      derives ConfigReader
  private final case class EnemyCfg(movementSpeed: Double, maxHealth: Double, damage: Double)
      derives ConfigReader
  private final case class SpawnerCfg(spawnRate: Double, maxEnemies: Int, spawnRadius: Double)
      derives ConfigReader
  private final case class GameCfg(player: PlayerCfg, enemy: EnemyCfg, spawner: SpawnerCfg)
      derives ConfigReader

  /** Walk up from the test's working directory to the repo-root `config/`. */
  private def findApplicationConf(): Option[File] =
    Iterator
      .iterate(new File(".").getAbsoluteFile)(_.getParentFile)
      .takeWhile(_ != null)
      .map(dir => new File(dir, "config/application.conf"))
      .find(_.exists())

  test("application.conf decodes via pureconfig include + substitution resolution") {
    findApplicationConf() match
      case None => fail("could not locate repo config/application.conf from the test working dir")
      case Some(appConf) =>
        val result = ConfigSource.file(appConf.getPath).load[GameCfg].left.map(_.prettyPrint())
        result match
          case Left(err) => fail(s"failed to load config: $err")
          case Right(cfg) =>
            // Substitutions: max-health comes from the shared max_health.conf.
            assertEquals(cfg.player.maxHealth, 100.0)
            assertEquals(cfg.enemy.maxHealth, 30.0)
            // Includes merged: values from each included file are present.
            assertEquals(cfg.player.movementSpeed, 200.0)
            assertEquals(cfg.player.autoAim, true)
            assertEquals(cfg.enemy.damage, 10.0)
            assertEquals(cfg.spawner.spawnRate, 1.5)
            assertEquals(cfg.spawner.maxEnemies, 10)
  }
