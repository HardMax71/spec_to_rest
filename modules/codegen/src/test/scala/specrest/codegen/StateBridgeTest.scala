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
        bridge.contains("await session.delete(url_mapping_row)"),
        s"persist must prune rows whose key left the map:\n$bridge"
      )
      assert(
        !bridge.contains("row.code ="),
        s"the key column is upsert-stable and must not be reassigned:\n$bridge"
      )

  test("todo_list state bridges enum and scalar-collection fields"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      StateBridge.plan(profiled) match
        case Left(reason) => fail(s"todo_list should be bridgeable: $reason")
        case Right(plan)  => assert(plan.hasState)
      val bridge = StateBridge.emit(profiled)
      assert(bridge.contains("enum_to_dafny(\"Status\", r.status)"), bridge)
      assert(
        bridge.contains("to_dafny_set(to_dafny_str(_x) for _x in r.tags)"),
        "tags should hydrate as a Dafny set of strings"
      )
      assert(
        bridge.contains("sorted(from_dafny_str(_x) for _x in "),
        "tags should persist as a sorted JSON list"
      )

  test("ecommerce state bridges the nested Set[LineItem] field"):
    SpecFixtures.loadIR("ecommerce").map: ir =>
      val profiled = Annotate.buildProfiledService(ir, "python-fastapi-postgres")
      StateBridge.plan(profiled) match
        case Left(reason) => fail(s"ecommerce should be bridgeable: $reason")
        case Right(plan)  => assert(plan.hasState)
      val bridge = StateBridge.emit(profiled)
      assert(
        bridge.contains("_line_item_by_id = {int(r.id): r for r in line_item_rows}"),
        "hydration should index line item rows by id"
      )
      assert(
        bridge.contains(
          "to_dafny_set(_line_item_to_dafny(_line_item_by_id[int(_x)]) for _x in r.items)"
        ),
        "items should hydrate by joining the id list against the line item rows"
      )
      assert(
        bridge.contains("def _line_item_to_dafny"),
        "the nested entity should get a to-dafny helper"
      )
      assert(
        bridge.contains("sorted(int(_x.id_) for _x in "),
        "items should persist as a sorted id list (id_ is the reserved-name selector)"
      )
      assert(
        bridge.contains("_line_item_post"),
        "line item rows should upsert from the post-state sets"
      )

  test("dafnyName doubles underscores like the Dafny Python backend"):
    assertEquals(StateBridge.dafnyName("created_at"), "created__at")
    assertEquals(StateBridge.dafnyName("base_url"), "base__url")
    assertEquals(StateBridge.dafnyName("plain"), "plain")
