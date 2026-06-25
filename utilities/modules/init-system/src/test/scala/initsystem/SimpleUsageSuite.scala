package initsystem

import gdext.classes.Node2D

class SimpleUsageSuite extends munit.FunSuite:
  case class Backing(
    speed: Double
  ) extends MakeInit[Backing.Init, Double]:
    def initInner(params: Double)(using ParentId, InitContext) =
      new Backing.Init:
        val computedSpeed = speed * params 

  object Backing:
    trait Init extends InitBase:
      def computedSpeed: Double

  test("params and backing values from through the factory") {
    given InitContext = InitContext()
    given ParentId = ParentId.root

    val initA = Backing(10).init(5)

    assertEquals(initA.computedSpeed, 50.toDouble)
  }
