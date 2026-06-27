package godothoccon

import pureconfig.{ConfigCursor, ConfigReader}
import pureconfig.error.CannotConvert

/** One entry in a [[ChanceTable]]: an item plus the chance it is kept on a roll. */
final case class ChanceEntry[A](item: A, chance: Percentage)

/** A random table that rolls every entry independently and keeps the successful
  * ones — a roll yields anywhere between none and all of the entries.
  *
  * Parsed from an object with a single `ChanceTable` key holding an array of
  * entries. A bare entry (not an object) is shorthand for a 100% chance, so the
  * two entries below are equivalent:
  *
  * {{{
  * drops {
  *   ChanceTable = [
  *     { item = "coins",     chance = "60%" },
  *     { item = "xp-bottle", chance = "80%" },
  *     "magnet"   // same as { item = "magnet", chance = "100%" }
  *   ]
  * }
  * }}}
  *
  * Ported from `framework/src/config/random_table.rs`.
  */
final case class ChanceTable[A](entries: List[ChanceEntry[A]]):

  /** One independent roll per entry; `unitRand` supplies values in `[0, 1)`. */
  def roll(unitRand: () => Float): List[A] =
    entries.filter(e => unitRand() < e.chance.fraction).map(_.item)

object ChanceTable:
  def empty[A]: ChanceTable[A] = ChanceTable(Nil)

  /** An always-successful entry. */
  def certain[A](item: A): ChanceEntry[A] = ChanceEntry(item, Percentage.Certain)

  given [A](using itemReader: ConfigReader[A]): ConfigReader[ChanceTable[A]] =
    ConfigReader.fromCursor: cur =>
      cur.asObjectCursor.flatMap: obj =>
        if obj.keys.size != 1 then
          cur.failed(
            CannotConvert(
              obj.keys.mkString("{", ", ", "}"),
              "ChanceTable",
              s"expected a single table-kind key, got ${obj.keys.size}"
            )
          )
        else
          val name = obj.keys.head
          if name != "ChanceTable" then
            cur.failed(CannotConvert(name, "ChanceTable", s"unknown random table kind '$name', expected ChanceTable"))
          else
            obj.atKey("ChanceTable").flatMap(_.asListCursor).flatMap: listCur =>
              val parsed = listCur.list.map(entryCur => parseEntry(entryCur, itemReader))
              parsed.collectFirst { case Left(f) => f } match
                case Some(failures) => Left(failures)
                case None => Right(ChanceTable(parsed.collect { case Right(e) => e }))

  private def parseEntry[A](
    cur: ConfigCursor,
    itemReader: ConfigReader[A]
  ): ConfigReader.Result[ChanceEntry[A]] =
    cur.asObjectCursor match
      // Shorthand: a bare value (e.g. an id string) is a 100%-chance entry.
      case Left(_) =>
        itemReader.from(cur).map(item => ChanceEntry(item, Percentage.Certain))
      case Right(obj) =>
        for
          itemCur <- obj.atKey("item")
          item <- itemReader.from(itemCur)
          chanceCur <- obj.atKey("chance")
          chance <- summon[ConfigReader[Percentage]].from(chanceCur)
        yield ChanceEntry(item, chance)
