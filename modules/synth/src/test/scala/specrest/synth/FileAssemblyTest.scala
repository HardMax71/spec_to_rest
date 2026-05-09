package specrest.synth

import munit.FunSuite

class FileAssemblyTest extends FunSuite:

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
