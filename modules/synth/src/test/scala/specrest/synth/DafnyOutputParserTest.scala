package specrest.synth

import munit.FunSuite

class DafnyOutputParserTest extends FunSuite:

  private val source =
    """method Increment(st: ServiceState)
      |  requires ServiceStateInv(st)
      |  ensures st.count == old(st.count) + 1
      |  ensures ServiceStateInv(st)
      |  modifies st
      |{
      |  st.count := st.count;
      |}""".stripMargin

  test("decodes Valid outcome with no errors"):
    val json =
      """{ "verificationResults": [
        |  { "name": "Increment", "outcome": "Correct", "vcResults": [
        |    { "vcNum": 1, "outcome": "Valid", "assertions": [] }
        |  ]}
        |]}""".stripMargin
    val r = DafnyOutputParser.parseLog(json, source).getOrElse(fail("should parse"))
    assertEquals(r.length, 1)
    assertEquals(r.head.name, "Increment")
    assertEquals(r.head.outcome, "Correct")
    assert(r.head.errors.isEmpty)

  test("decodes Invalid outcome with assertion-level errors"):
    val json =
      """{ "verificationResults": [
        |  { "name": "Increment", "outcome": "Errors", "vcResults": [
        |    { "vcNum": 1, "outcome": "Invalid", "assertions": [
        |      { "filename": "candidate.dfy", "line": 3, "col": 10,
        |        "description": "ensures clause that might not hold (postcondition)" }
        |    ]}
        |  ]}
        |]}""".stripMargin
    val r = DafnyOutputParser.parseLog(json, source).getOrElse(fail("should parse"))
    assertEquals(r.length, 1)
    val errs = r.head.errors
    assertEquals(errs.length, 1)
    val e = errs.head
    assertEquals(e.category, "postcondition_violation")
    assertEquals(e.line, Some(3))
    assertEquals(e.column, Some(10))
    assertEquals(e.relatedClause, Some("ensures st.count == old(st.count) + 1"))

  test("classifies via assertion description"):
    val descriptions = List(
      "a precondition for this call could not be proved" -> "precondition_violation",
      "this loop invariant might not hold on entry"      -> "loop_invariant_not_established",
      "loop invariant might not be maintained"           -> "loop_invariant_failure",
      "decreases expression might not decrease"          -> "decreases_failure",
      "assertion might not hold"                         -> "assertion_failure",
      "Type mismatch in assignment"                      -> "type_error"
    )
    descriptions.foreach: (description, expected) =>
      val json =
        s"""{ "verificationResults": [
           |  { "name": "X", "outcome": "Errors", "vcResults": [
           |    { "outcome": "Invalid", "assertions": [
           |      { "filename": "f.dfy", "line": 1, "col": 1, "description": ${quoted(
            description
          )} }
           |    ]}
           |  ]}
           |]}""".stripMargin
      val r = DafnyOutputParser.parseLog(json, "method X() {}").getOrElse(fail(""))
      assertEquals(r.head.errors.head.category, expected, s"for: $description")

  test("TimedOut method outcome maps to timeout"):
    val json =
      """{ "verificationResults": [
        |  { "name": "Slow", "outcome": "TimedOut", "vcResults": [
        |    { "outcome": "TimedOut", "assertions": [] }
        |  ]}
        |]}""".stripMargin
    val r = DafnyOutputParser.parseLog(json, "method Slow() {}").getOrElse(fail(""))
    val e = r.head.errors.head
    assertEquals(e.category, "timeout")

  test("invalid JSON returns Left"):
    val r = DafnyOutputParser.parseLog("not json at all", source)
    assert(r.isLeft)

  test("multi-method log filterable by name"):
    val json =
      """{ "verificationResults": [
        |  { "name": "A", "outcome": "Correct", "vcResults": [{ "outcome": "Valid", "assertions": [] }] },
        |  { "name": "B", "outcome": "Errors", "vcResults": [
        |    { "outcome": "Invalid", "assertions": [
        |      { "filename": "f.dfy", "line": 5, "col": 1, "description": "a postcondition could not be proved" }
        |    ]}
        |  ]}
        |]}""".stripMargin
    val r = DafnyOutputParser.parseLog(json, source).getOrElse(fail(""))
    assertEquals(r.map(_.name), List("A", "B"))
    assert(r.find(_.name == "A").exists(_.errors.isEmpty))
    assert(r.find(_.name == "B").exists(_.errors.nonEmpty))

  test("VerifierRun.verifiedFor handles Dafny's '(well-formedness)'/'(correctness)' suffix"):
    val pass = VerifierRun(
      methods = List(
        DafnyOutputParser.MethodResult("Increment (well-formedness)", "Correct", Nil),
        DafnyOutputParser.MethodResult("Increment (correctness)", "Correct", Nil),
        DafnyOutputParser.MethodResult("ServiceState.Inv (well-formedness)", "Correct", Nil)
      ),
      rawStdout = "",
      rawStderr = "",
      exitCode = 0,
      durationMs = 0L
    )
    assert(pass.verifiedFor("Increment"))
    assertEquals(pass.errorsFor("Increment"), Nil)

    val err = VerifierError(
      category = "postcondition_violation",
      message = "this postcondition holds",
      line = Some(9)
    )
    val fail = pass.copy(methods =
      pass.methods.updated(
        1,
        DafnyOutputParser.MethodResult("Increment (correctness)", "Errors", List(err))
      )
    )
    assert(!fail.verifiedFor("Increment"))
    assertEquals(fail.errorsFor("Increment"), List(err))

  test("real Dafny 4.11 JSON: filters auto-inserted assertions, keeps user-facing failures"):
    val json =
      """{ "verificationResults": [
        |  { "name": "ServiceState.Inv (well-formedness)", "outcome": "Correct", "vcResults": [
        |    { "vcNum": 1, "outcome": "Valid", "assertions": [
        |      { "filename": "f.dfy", "line": 3, "col": 32, "description": "sufficient reads clause to read field" }
        |    ]}
        |  ]},
        |  { "name": "Increment (well-formedness)", "outcome": "Correct", "vcResults": [
        |    { "vcNum": 1, "outcome": "Valid", "assertions": [
        |      { "filename": "f.dfy", "line": 8, "col": 18, "description": "target object is never null" }
        |    ]}
        |  ]},
        |  { "name": "Increment (correctness)", "outcome": "Errors", "vcResults": [
        |    { "vcNum": 1, "outcome": "Invalid", "assertions": [
        |      { "filename": "f.dfy", "line": 12, "col": 6,  "description": "target object is never null" },
        |      { "filename": "f.dfy", "line": 12, "col": 6,  "description": "an object is in the enclosing context's modifies clause" },
        |      { "filename": "f.dfy", "line": 9,  "col": 20, "description": "this postcondition holds" },
        |      { "filename": "f.dfy", "line": 10, "col": 17, "description": "this postcondition holds" }
        |    ]}
        |  ]}
        |]}""".stripMargin
    val r = DafnyOutputParser.parseLog(json, source).getOrElse(fail("should parse"))
    assertEquals(r.length, 3)
    val correctness = r.find(_.name == "Increment (correctness)").getOrElse(fail("missing"))
    assertEquals(correctness.errors.length, 2, "noise filtered out")
    assertEquals(correctness.errors.map(_.category).distinct, List("postcondition_violation"))
    assertEquals(correctness.errors.map(_.line), List(Some(9), Some(10)))

  private def quoted(s: String): String =
    "\"" + s.replace("\"", "\\\"") + "\""
