package initsystem

import initsystem.TestSupport.countInits

class StorageManagementSuite extends munit.FunSuite:

  test("free removes an init from storage") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = Backing.init(())
    ctx.processPending()
    assertEquals(countInits(ctx), 1)
    init.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("freeing a parent cascades to children") {
    given ctx: InitContext = InitContext()
    val parent =
      given ParentId = ParentId.root
      Backing.init(())
    ctx.processPending()
    locally {
      given ParentId = ParentId(parent.selfId)
      Backing.init(())
      Backing.init(())
    }
    ctx.processPending()
    assertEquals(countInits(ctx), 3)
    parent.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("multiple inits coexist with unique ids") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val a = Backing.init(())
    val b = Backing.init(())
    val c = Backing.init(())
    ctx.processPending()
    assertEquals(countInits(ctx), 3)
    assertNotEquals(a.selfId.value, b.selfId.value)
    assertNotEquals(b.selfId.value, c.selfId.value)
    assertNotEquals(a.selfId.value, c.selfId.value)
  }

  test("double free does not throw") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = Backing.init(())
    ctx.processPending()
    init.free()
    init.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("onFree is called once on removal") {
    val counter = FreeCounter()
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = TrackedBacking(counter).init(())
    ctx.processPending()
    init.free()
    ctx.processPending()
    assertEquals(counter.count, 1)
  }

  test("onFree is called for every init in a cascade") {
    val counter = FreeCounter()
    given ctx: InitContext = InitContext()
    val backing = TrackedBacking(counter)
    val parent =
      given ParentId = ParentId.root
      backing.init(())
    ctx.processPending()
    locally {
      given ParentId = ParentId(parent.selfId)
      backing.init(())
      backing.init(())
    }
    ctx.processPending()
    parent.free()
    ctx.processPending()
    assertEquals(counter.count, 3)
  }

  test("add then free before processPending never enters storage") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = Backing.init(())
    init.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("a deep chain fully cascades on root free") {
    given ctx: InitContext = InitContext()
    val grandparent =
      given ParentId = ParentId.root
      Backing.init(())
    ctx.processPending()
    val parent =
      given ParentId = ParentId(grandparent.selfId)
      Backing.init(())
    ctx.processPending()
    val child =
      given ParentId = ParentId(parent.selfId)
      Backing.init(())
    ctx.processPending()
    locally {
      given ParentId = ParentId(child.selfId)
      Backing.init(())
    }
    ctx.processPending()
    assertEquals(countInits(ctx), 4)
    grandparent.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("forEach on empty storage does not invoke the callback") {
    val ctx = InitContext()
    var called = false
    ctx.forEach(_ => called = true)
    assert(!called)
  }

  test("freeing an unknown id does not throw") {
    val ctx = InitContext()
    ctx.free(InitId.random())
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("repeated processPending on stable storage is a no-op") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    Backing.init(())
    ctx.processPending()
    ctx.processPending()
    ctx.processPending()
    assertEquals(countInits(ctx), 1)
  }

  test("child freed before parent leaves storage clean") {
    given ctx: InitContext = InitContext()
    val parent =
      given ParentId = ParentId.root
      Backing.init(())
    ctx.processPending()
    val child =
      given ParentId = ParentId(parent.selfId)
      Backing.init(())
    ctx.processPending()
    child.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 1)
    parent.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("free called from inside onFree is deferred to the next processPending") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val sibling = SiblingRef()
    val trigger = FreesOnFreeBacking(sibling).init(())
    ctx.processPending()
    val sib = Backing.init(())
    ctx.processPending()
    sibling.id = sib.selfId
    trigger.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 1) // re-entrant free survived
    ctx.processPending()
    assertEquals(countInits(ctx), 0) // flushed on the next call
  }

  test("the sentinel id used as a real init id does not corrupt storage") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val child = Backing.init(())
    ctx.processPending()
    assertEquals(countInits(ctx), 1)
    child.free()
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("a grandchild added the same frame the grandparent is freed is removed by cascade") {
    given ctx: InitContext = InitContext()
    val parent =
      given ParentId = ParentId.root
      Backing.init(())
    ctx.processPending()
    val child =
      given ParentId = ParentId(parent.selfId)
      Backing.init(())
    ctx.processPending()
    parent.free()
    locally {
      given ParentId = ParentId(child.selfId)
      Backing.init(())
    }
    ctx.processPending()
    assertEquals(countInits(ctx), 0)
  }

  test("getInit returns the init by id and None after free") {
    given ctx: InitContext = InitContext()
    given ParentId = ParentId.root
    val init = Backing.init(())
    val id = init.selfId
    assert(ctx.getInit(id).isEmpty)
    ctx.processPending()
    assert(ctx.getInit(id).exists(_.selfId.value == id.value))
    init.free()
    ctx.processPending()
    assert(ctx.getInit(id).isEmpty)
  }
