package game.config

import java.io.File

import pureconfig.ConfigSource

import godothoccon.Loader

/** Locates the repo-root `config/` directory and decodes it into a typed
  * [[GameConfig]].
  *
  * Like survivor-game, the watcher loads ONLY `application.conf` and lets the
  * HOCON backend follow its `include` directives — the set of config files is
  * owned by that file, not by Scala. survivor-game (Rust + hocon-rs) had the
  * HOCON parser resolve the includes; the Scala-Native HOCON backend behind the
  * pureconfig fork (the SHocon shim) now does the same, AND resolves the
  * `${...}` substitutions across the merged tree. So we just point pureconfig at
  * `application.conf` and decode: `ConfigSource.file` resolves the `include`s
  * (relative to the included file's directory) and the substitutions for us — no
  * manual include scanning or `withFallback` plumbing in Scala any more.
  *
  * The framework [[godothoccon.ConfigWatcher]] watches the whole directory tree, so
  * editing `application.conf` or any included file triggers a reload.
  */
object ConfigLoader:

  /** Find `config/` (searching the same roots as the framework loader) and
    * decode the config reachable from `application.conf`. */
  def load(): Either[String, GameConfig] =
    Loader.findConfigDirectory().flatMap(load)

  /** Decode the config from an explicit config directory, starting at its
    * `application.conf`. pureconfig resolves the `include` directives and
    * `${...}` substitutions; unknown top-level keys (e.g. the shared
    * `max-health` block referenced by player/enemy) are ignored by the derived
    * reader. */
  def load(configDir: File): Either[String, GameConfig] =
    val applicationConf = File(configDir, Loader.ConfigFileName)
    ConfigSource.file(applicationConf.getPath).load[GameConfig].left.map(_.prettyPrint())
