# `config/`

The HOCON tree loaded at runtime by the test game's config watcher.

Include **order matters**: `max_health.conf` is included before `player.conf` and
`enemy.conf` because those reference it via `${max-health.*}`.

Adding a config file means adding the `.conf` plus one `include required(...)` line in
`application.conf` — no Scala change, unless you want it typed, in which case add the
field to `GameConfig` (`scala/src/main/scala/game/config/`).
