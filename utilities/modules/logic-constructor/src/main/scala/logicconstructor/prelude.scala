package logicconstructor

/** Convenient single-import surface, mirroring the Rust crate's `prelude`.
  *
  * {{{
  * import logicconstructor.prelude.*
  * }}}
  *
  * Re-exports the public types, `runLca`, and the parsers (which live in the
  * `logicconstructor.parser` package).
  */
object prelude:
  export logicconstructor.{
    ConfigValue,
    CollisionKind,
    LcEntityType,
    LcEntityTypeId,
    LcEntity,
    LcAction,
    LcSingleActionConfig,
    LcConfigRaw,
    LcActionConfig,
    LcSourceWithAction,
    runLca
  }
  export logicconstructor.parser.{
    ParseEffect,
    parseCollisionKind,
    parseLcConfigRaw,
    parseLcConfig,
    parseLcConfigListRaw,
    parseLcConfigList,
    parseLcActionConfig
  }
