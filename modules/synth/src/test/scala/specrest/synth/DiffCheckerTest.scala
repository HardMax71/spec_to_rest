package specrest.synth

import munit.CatsEffectSuite
import specrest.convention.dafny.DafnyMethodHeader

class DiffCheckerTest extends CatsEffectSuite:

  private val header = DafnyMethodHeader(
    name = "Increment",
    signature = "method Increment(st: ServiceState)",
    requiresClauses = List("ServiceStateInv(st)"),
    ensuresClauses = List("st.count == old(st.count) + 1", "ServiceStateInv(st)"),
    modifiesClauses = List("st")
  )

  test("matching clauses pass"):
    val candidate =
      """method Increment(st: ServiceState)
        |  requires ServiceStateInv(st)
        |  ensures st.count == old(st.count) + 1
        |  ensures ServiceStateInv(st)
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}""".stripMargin
    assertEquals(DiffChecker.check(header, candidate), Right(()))

  test("missing ensures clause fails"):
    val candidate =
      """method Increment(st: ServiceState)
        |  requires ServiceStateInv(st)
        |  ensures st.count == old(st.count) + 1
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}""".stripMargin
    DiffChecker.check(header, candidate) match
      case Right(_) => fail("expected diff violation")
      case Left(violation) =>
        assert(violation.diff.contains("ServiceStateInv(st)"))

  test("weakened postcondition fails"):
    val candidate =
      """method Increment(st: ServiceState)
        |  requires ServiceStateInv(st)
        |  ensures st.count >= old(st.count)
        |  ensures ServiceStateInv(st)
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}""".stripMargin
    assert(DiffChecker.check(header, candidate).isLeft)

  test("introducing {:extern} declaration is rejected"):
    val candidate =
      """function {:extern "Mod", "fn"} bad(): int
        |
        |method Increment(st: ServiceState)
        |  requires ServiceStateInv(st)
        |  ensures st.count == old(st.count) + 1
        |  ensures ServiceStateInv(st)
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}""".stripMargin
    DiffChecker.check(header, candidate) match
      case Right(_) => fail("expected extern violation")
      case Left(v)  => assert(v.message.contains("extern"))

  test("normalization tolerates whitespace and trailing semicolons"):
    val candidate =
      """method Increment(st: ServiceState)
        |  requires    ServiceStateInv(st);
        |  ensures   st.count == old(st.count) + 1
        |  ensures ServiceStateInv(st);
        |  modifies   st
        |{ st.count := st.count + 1; }""".stripMargin
    assertEquals(DiffChecker.check(header, candidate), Right(()))

  test("missing method declaration fails"):
    val r = DiffChecker.check(header, "{ no method here }")
    assert(r.isLeft)

  test("clause containing a set literal is not truncated at the inner '{'"):
    val setHeader = DafnyMethodHeader(
      name = "Archive",
      signature = "method Archive(st: ServiceState, id: int)",
      requiresClauses = List("id in st.todos", "st.todos[id].status in {TODO, DONE}"),
      ensuresClauses = List("st.todos[id].status == ARCHIVED"),
      modifiesClauses = List("st")
    )
    val candidate =
      """method Archive(st: ServiceState, id: int)
        |  requires id in st.todos
        |  requires st.todos[id].status in {TODO, DONE}
        |  ensures st.todos[id].status == ARCHIVED
        |  modifies st
        |{
        |  st.todos := st.todos[id := st.todos[id].(status := ARCHIVED)];
        |}""".stripMargin
    assertEquals(DiffChecker.check(setHeader, candidate), Right(()))

  test("clause containing nested braces (forall trigger) is preserved"):
    val triggerHeader = DafnyMethodHeader(
      name = "Quant",
      signature = "method Quant(st: ServiceState)",
      requiresClauses = List("forall x :: x in st.s ==> {x} != {}"),
      ensuresClauses = List("st.s == old(st.s)"),
      modifiesClauses = List("st")
    )
    val candidate =
      """method Quant(st: ServiceState)
        |  requires forall x :: x in st.s ==> {x} != {}
        |  ensures st.s == old(st.s)
        |  modifies st
        |{
        |  return;
        |}""".stripMargin
    assertEquals(DiffChecker.check(triggerHeader, candidate), Right(()))
