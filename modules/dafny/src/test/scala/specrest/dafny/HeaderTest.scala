package specrest.dafny

import munit.CatsEffectSuite
import specrest.convention.testutil.SpecFixtures

class HeaderTest extends CatsEffectSuite:

  test("safe_counter — Increment header has expected requires/ensures/modifies"):
    SpecFixtures.loadIR("safe_counter").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val inc = out.methods.find(_.name == "Increment").getOrElse(fail("Increment missing"))
      assertEquals(inc.signature, "method Increment(st: ServiceState)")
      assertEquals(inc.modifiesClauses, List("st"))
      assertEquals(inc.requiresClauses, List("ServiceStateInv(st)"))
      assertEquals(
        inc.ensuresClauses,
        List("st.count == old(st.count) + 1", "ServiceStateInv(st)")
      )

  test("safe_counter — Decrement header carries the requires count > 0 clause"):
    SpecFixtures.loadIR("safe_counter").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val dec = out.methods.find(_.name == "Decrement").getOrElse(fail("Decrement missing"))
      assert(
        dec.requiresClauses.contains("st.count > 0"),
        s"requires should include `st.count > 0`; got ${dec.requiresClauses}"
      )

  test("url_shortener — Delete is a DELETE-shaped method (no returns, mutates st)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val del = out.methods.find(_.name == "Delete").getOrElse(fail("Delete missing"))
      assertEquals(del.modifiesClauses, List("st"))
      assert(
        del.signature.startsWith("method Delete(st: ServiceState"),
        s"unexpected signature: ${del.signature}"
      )
      assert(
        !del.signature.contains(" returns "),
        s"Delete should have no returns; got ${del.signature}"
      )

  test("url_shortener — Shorten requires LongURLWhere(url) (alias-where lifted on inputs)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val sh  = out.methods.find(_.name == "Shorten").getOrElse(fail("Shorten missing"))
      assert(
        sh.requiresClauses.contains("LongURLWhere(url)"),
        s"requires should include LongURLWhere(url); got ${sh.requiresClauses}"
      )

  test("url_shortener — Resolve does NOT add ensures LongURLWhere(url) (output not constructed)"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val rv  = out.methods.find(_.name == "Resolve").getOrElse(fail("Resolve missing"))
      assert(
        !rv.ensuresClauses.exists(_.contains("LongURLWhere")),
        s"Resolve must not auto-emit LongURLWhere(url) ensures; got ${rv.ensuresClauses}"
      )

  test("todo_list — UpdateTodo unwraps Option-typed inputs in equality clauses"):
    SpecFixtures.loadIR("todo_list").map: ir =>
      val out = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val up  = out.methods.find(_.name == "UpdateTodo").getOrElse(fail("UpdateTodo missing"))
      assert(
        up.ensuresClauses.exists(_.contains("todo.title == title.value")),
        s"ensures should unwrap title.value; got ${up.ensuresClauses}"
      )

  test("generator output is deterministic across invocations"):
    SpecFixtures.loadIR("url_shortener").map: ir =>
      val first  = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      val second = Generator.generate(ir).toOption.getOrElse(fail("generator failed"))
      assertEquals(first.text, second.text)
      assertEquals(first.methods, second.methods)
