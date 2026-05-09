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
