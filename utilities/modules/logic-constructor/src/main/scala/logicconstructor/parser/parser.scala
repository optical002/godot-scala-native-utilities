package logicconstructor
package parser

import pureconfig.{ConfigReader, ConfigSource}

/** Parses logic-constructor HOCON via pureconfig.
  *
  * Decoding is entirely driven by [[ConfigReader]] instances (see [[LcAction.Config.singleReader]],
  * [[LcAction.Config.listReader]] and the [[CollisionKind]] reader), so there are no hand-written
  * parsers — only thin entry points that load a value at a given path.
  */

/** Loads an action list from the value at `path` in a HOCON document, e.g.
  * `parseActions("actions", hocon)` for `actions = [ ... ]`.
  */
def parseActions[A <: LcEntity.Type](path: String, hocon: String)(using
  ConfigReader[LcAction.Config[A]]
): Either[String, LcAction.Config[A]] =
  ConfigSource.string(hocon).at(path).load[LcAction.Config[A]].left.map(_.prettyPrint())

/** Loads a single action from the value at `path` in a HOCON document. */
def parseAction[A <: LcEntity.Type](path: String, hocon: String)(using
  ConfigReader[LcAction.Config.Single[A]]
): Either[String, LcAction.Config.Single[A]] =
  ConfigSource.string(hocon).at(path).load[LcAction.Config.Single[A]].left.map(_.prettyPrint())
