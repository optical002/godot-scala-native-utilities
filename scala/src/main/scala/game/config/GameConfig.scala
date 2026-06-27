package game.config

import pureconfig.ConfigReader

/** Typed view of the repo-root `config/` directory.
  *
  * The structure mirrors the HOCON in `config/application.conf` and its
  * includes: a `player`, `enemy`, and `spawner` block. pureconfig maps each
  * camelCase field to its kebab-cased HOCON key by default (so `movementSpeed`
  * reads `movement-speed`), exactly as survivor-game's Rust `GameParser` did.
  *
  * `derives ConfigReader` is the Scala-Native pureconfig fork's equivalent of
  * the Rust `#[derive(GameParser)]` macro — it generates the decoder used by
  * [[godothoccon.Loader]].
  */
final case class GameConfig(
  player: PlayerConfig,
  enemy: EnemyConfig,
  spawner: SpawnerConfig
) derives ConfigReader

/** `config/game/player.conf` — `player { ... }`. */
final case class PlayerConfig(
  movementSpeed: Double,
  maxHealth: Double,
  autoAim: Boolean
) derives ConfigReader

/** `config/game/enemy.conf` — `enemy { ... }`. */
final case class EnemyConfig(
  movementSpeed: Double,
  maxHealth: Double,
  damage: Double
) derives ConfigReader

/** `config/game/spawner.conf` — `spawner { ... }`. */
final case class SpawnerConfig(
  spawnRate: Double,
  maxEnemies: Int,
  spawnRadius: Double
) derives ConfigReader

object GameConfig:

  /** Render the full config as aligned `key = value` lines, used to log the
    * initial snapshot. */
  def describe(c: GameConfig): String =
    val lines = fields(c)
    val width = lines.map(_._1.length).maxOption.getOrElse(0)
    lines.map((k, v) => s"  ${k.padTo(width, ' ')} = $v").mkString("\n")

  /** Flatten a config into dotted `path -> value` pairs, in a stable order.
    * The reload diff compares two of these maps. */
  def fields(c: GameConfig): List[(String, String)] =
    List(
      "player.movement-speed" -> c.player.movementSpeed.toString,
      "player.max-health" -> c.player.maxHealth.toString,
      "player.auto-aim" -> c.player.autoAim.toString,
      "enemy.movement-speed" -> c.enemy.movementSpeed.toString,
      "enemy.max-health" -> c.enemy.maxHealth.toString,
      "enemy.damage" -> c.enemy.damage.toString,
      "spawner.spawn-rate" -> c.spawner.spawnRate.toString,
      "spawner.max-enemies" -> c.spawner.maxEnemies.toString,
      "spawner.spawn-radius" -> c.spawner.spawnRadius.toString
    )

  /** The fields whose values differ between two configs, as
    * `(path, oldValue, newValue)`. Drives the "log only what changed" output. */
  def diff(prev: GameConfig, next: GameConfig): List[(String, String, String)] =
    val nextMap = fields(next).toMap
    fields(prev).collect:
      case (k, oldV) if nextMap(k) != oldV => (k, oldV, nextMap(k))
