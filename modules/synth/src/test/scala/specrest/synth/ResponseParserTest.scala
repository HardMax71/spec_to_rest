package specrest.synth

import munit.FunSuite

class ResponseParserTest extends FunSuite:

  test("extracts code block from ```dafny fence"):
    val resp =
      """Here is the body:
        |```dafny
        |method Foo() {
        |  return 0;
        |}
        |```
        |Hope it helps.""".stripMargin
    assertEquals(
      ResponseParser.extractCodeBlock(resp).map(_.trim),
      Right("method Foo() {\n  return 0;\n}")
    )

  test("falls back to plain ``` fence"):
    val resp = "```\nbody\n```"
    assertEquals(ResponseParser.extractCodeBlock(resp), Right("body"))

  test("rejects response without any fenced block"):
    assert(ResponseParser.extractCodeBlock("no fence here").isLeft)

  test("rejects unterminated fence"):
    assert(ResponseParser.extractCodeBlock("```dafny\nopen forever").isLeft)

  test("extractMethodBody finds method by name and returns its braces content"):
    val code =
      """method Increment(st: ServiceState)
        |  modifies st
        |{
        |  st.count := st.count + 1;
        |}""".stripMargin
    assertEquals(
      ResponseParser.extractMethodBody(code, "Increment"),
      Right("  st.count := st.count + 1;")
    )

  test("extractMethodBody handles nested braces"):
    val code =
      """method Foo() {
        |  if (true) { x := 1; } else { x := 2; }
        |}""".stripMargin
    assertEquals(
      ResponseParser.extractMethodBody(code, "Foo"),
      Right("  if (true) { x := 1; } else { x := 2; }")
    )

  test("extractMethodBody skips braces inside string literals"):
    val code =
      """method Foo() {
        |  s := "}{nope";
        |  t := 1;
        |}""".stripMargin
    assertEquals(
      ResponseParser.extractMethodBody(code, "Foo"),
      Right("""  s := "}{nope";
              |  t := 1;""".stripMargin)
    )

  test("extractMethodBody skips braces inside line comments"):
    val code =
      """method Foo() {
        |  // }} ignore
        |  x := 1;
        |}""".stripMargin
    assertEquals(
      ResponseParser.extractMethodBody(code, "Foo"),
      Right("  // }} ignore\n  x := 1;")
    )

  test("extractMethodBody falls back to first { when method name absent"):
    val code = "{\n  x := 1;\n}"
    assertEquals(ResponseParser.extractMethodBody(code, "Foo"), Right("  x := 1;"))
