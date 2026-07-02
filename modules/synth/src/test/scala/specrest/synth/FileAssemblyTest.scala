package specrest.synth

import munit.CatsEffectSuite

class FileAssemblyTest extends CatsEffectSuite:

  private val skeleton =
    """class ServiceState { var count: int }
      |
      |method Increment(st: ServiceState)
      |  modifies st
      |  requires ServiceStateInv(st)
      |  ensures st.count == old(st.count) + 1
      |{
      |  // YOUR CODE HERE
      |}
      |
      |method Reset(st: ServiceState)
      |  modifies st
      |  ensures st.count == 0
      |{
      |  // YOUR CODE HERE
      |}
      |""".stripMargin

  test("splices body into named method, leaves siblings untouched"):
    val r = FileAssembly
      .splice(skeleton, "Increment", "st.count := st.count + 1;")
      .getOrElse(fail("splice failed"))
    assert(r.contains("st.count := st.count + 1;"))
    assert(r.contains("method Reset"))
    assert(r.indexOf("// YOUR CODE HERE") >= 0, "Reset's placeholder still present")
    assertEquals(r.split("// YOUR CODE HERE").length, 2, "exactly one placeholder remains")

  test("body lines are indented two spaces"):
    val r = FileAssembly
      .splice(skeleton, "Increment", "var x := 1;\nst.count := st.count + x;")
      .getOrElse(fail("splice failed"))
    assert(r.contains("  var x := 1;"))
    assert(r.contains("  st.count := st.count + x;"))

  test("missing method returns SpliceFailure"):
    val r = FileAssembly.splice(skeleton, "Nonexistent", "...")
    assert(r.isLeft)

  test(
    "if target method has no placeholder, splice fails instead of bleeding into the next method"
  ):
    val targetAlreadyFilled =
      """method Increment(st: ServiceState)
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}
        |
        |method Reset(st: ServiceState)
        |  modifies st
        |{
        |  // YOUR CODE HERE
        |}
        |""".stripMargin
    val r = FileAssembly.splice(targetAlreadyFilled, "Increment", "INTRUDER_BODY")
    assert(r.isLeft, "must NOT splice into Reset's placeholder")
    assert(targetAlreadyFilled.contains("// YOUR CODE HERE"), "Reset placeholder untouched")

  test("spliceAll fills every method and propagates the first failure"):
    val r = FileAssembly
      .spliceAll(
        skeleton,
        Map(
          "Increment" -> FileAssembly.MethodPart("st.count := st.count + 1;"),
          "Reset"     -> FileAssembly.MethodPart("st.count := 0;")
        )
      )
      .getOrElse(fail("spliceAll failed"))
    assert(r.contains("st.count := st.count + 1;"), "Increment body present")
    assert(r.contains("st.count := 0;"), "Reset body present")
    assert(!r.contains("// YOUR CODE HERE"), "no placeholders remain")

  test("spliceAll injects a part's helper declarations before its method"):
    val part = FileAssembly.MethodPart(
      body = "st.count := Bump(st.count);",
      helpers = "function Bump(n: int): int { n + 1 }"
    )
    val r = FileAssembly
      .spliceAll(skeleton, Map("Increment" -> part))
      .getOrElse(fail("spliceAll failed"))
    assert(r.contains("function Bump(n: int): int { n + 1 }"), r)
    assert(
      r.indexOf("function Bump") < r.indexOf("method Increment"),
      "helpers must precede the method they serve"
    )
    assert(r.contains("st.count := Bump(st.count);"), r)

  test("spliceAll fails if a method is missing in the skeleton"):
    val r = FileAssembly.spliceAll(
      skeleton,
      Map(
        "Increment" -> FileAssembly.MethodPart("ok;"),
        "Ghost"     -> FileAssembly.MethodPart("no;")
      )
    )
    assert(r.isLeft, s"expected Left, got $r")

  test("anchor matches method name with parameters but not method-name prefix"):
    val sk =
      """method IncrementBy(st: ServiceState, n: int)
        |{
        |  // YOUR CODE HERE
        |}
        |
        |method Increment(st: ServiceState)
        |{
        |  // YOUR CODE HERE
        |}
        |""".stripMargin
    val r = FileAssembly.splice(sk, "Increment", "INCREMENT_BODY").getOrElse(fail(""))
    assert(r.contains("INCREMENT_BODY"))
    val incrIdx                  = r.indexOf("method Increment(st")
    val placeholderInIncrementBy = r.indexOf("// YOUR CODE HERE")
    assert(
      placeholderInIncrementBy >= 0 && placeholderInIncrementBy < incrIdx,
      "IncrementBy placeholder is the one preserved"
    )
