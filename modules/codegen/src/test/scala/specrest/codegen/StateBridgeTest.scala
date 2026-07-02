package specrest.codegen

import munit.CatsEffectSuite
import specrest.codegen.python.StateBridge
import specrest.codegen.testutil.SpecFixtures
import specrest.profile.Annotate

class StateBridgeTest extends CatsEffectSuite:

  test("url_shortener state bridges: both relations hydrate, entity rows persist"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      val plan     = StateBridge.plan(profiled).toOption.getOrElse(fail("expected a bridge plan"))
      assert(StateBridge.hasState(plan), "url_shortener state should be bridgeable")
      val bridge = StateBridge.emit(profiled)
      assert(bridge.contains("async def hydrate_state"), bridge)
      assert(bridge.contains("st.store = to_dafny_map({"), bridge)
      assert(
        bridge.contains("module_.UrlMapping_UrlMapping("),
        s"entity rows must hydrate through the Dafny constructor:\n$bridge"
      )
      assert(
        bridge.contains("value.created__at") && bridge.contains("value.click__count"),
        s"Dafny field access must double underscores:\n$bridge"
      )
      assert(
        bridge.contains("await session.delete(row)"),
        s"persist must prune rows whose key left the map:\n$bridge"
      )
      assert(
        !bridge.contains("row.code ="),
        s"the key column is upsert-stable and must not be reassigned:\n$bridge"
      )

  test("todo_list state is not bridgeable (enum and collection fields)"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      StateBridge.plan(profiled) match
        case Left(reason) =>
          assert(reason.contains("Todo"), s"reason should name the entity: $reason")
        case Right(_) => fail("todo_list should not be bridgeable yet")

  test("dafnyName doubles underscores like the Dafny Python backend"):
    assertEquals(StateBridge.dafnyName("created_at"), "created__at")
    assertEquals(StateBridge.dafnyName("base_url"), "base__url")
    assertEquals(StateBridge.dafnyName("plain"), "plain")
