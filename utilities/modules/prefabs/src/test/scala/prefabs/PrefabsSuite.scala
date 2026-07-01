package prefabs

import com.typesafe.config.ConfigFactory

import gdext.classes.Node

/** Standalone coverage for the extracted prefab flow: the `path → root type`
  * index parsing and the `Prefab`/`PrefabGroup` resolution logic, none of which
  * touch a live Godot object (scene resolution is deferred to instantiate-time).
  * Proves the module works outside the game it was extracted from.
  */
class PrefabsSuite extends munit.FunSuite:

  // A dummy registered-class type whose simple name is the prefab root type.
  final class Hero extends Node
  final class Villain extends Node

  // The methods under test never dereference the Prefabs resource (resolution is
  // deferred), so a null resource is fine for pure index/type-checking tests.
  private val noPrefabs: Prefabs = null.asInstanceOf[Prefabs]

  private def ctxOf(pairs: (String, String)*): ParseCtx =
    ParseCtx(pairs.toMap, noPrefabs)

  // --- ParseCtx.extractKnownPrefabs (HOCON `prefabs { … }` walking) ----------

  test("extractKnownPrefabs flattens nested prefab records into path -> type") {
    val doc = ConfigFactory.parseString(
      """prefabs {
        |  hero = { path = "actors/hero", type = "Hero" }
        |  enemies {
        |    grunt = { path = "actors/enemies/grunt", type = "Villain" }
        |  }
        |}""".stripMargin
    )
    val section = Some(doc.root().get("prefabs"))
    assertEquals(
      ParseCtx.extractKnownPrefabs(section),
      Right(Map(
        "actors/hero" -> "Hero",
        "actors/enemies/grunt" -> "Villain"
      ))
    )
  }

  test("extractKnownPrefabs returns empty for an absent section") {
    assertEquals(ParseCtx.extractKnownPrefabs(None), Right(Map.empty))
  }

  // --- Prefab.fromPath -------------------------------------------------------

  test("Prefab.fromPath resolves a registered path of the right type") {
    val ctx = ctxOf("actors/hero" -> "Hero")
    val r = Prefab.fromPath[Hero]("actors/hero", ctx)
    assert(r.isRight, r)
    assertEquals(r.toOption.get.path, "actors/hero")
  }

  test("Prefab.fromPath rejects a type mismatch") {
    val ctx = ctxOf("actors/hero" -> "Villain")
    assert(Prefab.fromPath[Hero]("actors/hero", ctx).isLeft)
  }

  test("Prefab.fromPath rejects an unregistered path") {
    assert(Prefab.fromPath[Hero]("nope", ctxOf()).isLeft)
  }

  // --- PrefabGroup.fromPrefix ------------------------------------------------

  test("PrefabGroup.fromPrefix collects matching prefabs sorted by path") {
    val ctx = ctxOf(
      "enemies/b" -> "Villain",
      "enemies/a" -> "Villain",
      "heroes/x" -> "Hero"
    )
    val g = PrefabGroup.fromPrefix[Villain]("enemies", ctx)
    assert(g.isRight, g)
    assertEquals(g.toOption.get.prefabs.map(_.path), Vector("enemies/a", "enemies/b"))
  }

  test("PrefabGroup.fromPrefix errors on an empty group") {
    assert(PrefabGroup.fromPrefix[Villain]("missing", ctxOf()).isLeft)
  }

  test("PrefabGroup.fromPrefix errors on a type mismatch within the group") {
    val ctx = ctxOf("enemies/a" -> "Hero")
    assert(PrefabGroup.fromPrefix[Villain]("enemies", ctx).isLeft)
  }
