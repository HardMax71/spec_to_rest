package specrest.codegen.migration

import munit.CatsEffectSuite

class AlembicSyntaxTest extends CatsEffectSuite:

  test("tripleQuoted wraps a plain SQL body in Python triple quotes"):
    val body = "SELECT 1;"
    val out  = AlembicSyntax.tripleQuoted(body)
    assertEquals(out, "\"\"\"\nSELECT 1;\n\"\"\"")

  test("tripleQuoted escapes embedded triple-quotes so Python reads back the original"):
    // body literally contains three consecutive double-quote chars.
    val body         = "x\"\"\"y"
    val out          = AlembicSyntax.tripleQuoted(body)
    val expectedSafe = "x\"\"\\\"y"
    assertEquals(out, "\"\"\"\n" + expectedSafe + "\n\"\"\"")
    val pythonDecoded = decodePythonStringLiteral(out)
    assertEquals(pythonDecoded, "\n" + body + "\n")

  test("tripleQuoted escapes embedded backslashes so SQL backslash survives Python decoding"):
    val body          = "regex \\d+"
    val out           = AlembicSyntax.tripleQuoted(body)
    val pythonDecoded = decodePythonStringLiteral(out)
    assertEquals(pythonDecoded, "\n" + body + "\n")

  test("tripleQuoted preserves SQL with no metacharacters"):
    val body = "CREATE TRIGGER trg ON t FOR EACH ROW EXECUTE FUNCTION fn();"
    val out  = AlembicSyntax.tripleQuoted(body)
    assertEquals(decodePythonStringLiteral(out), "\n" + body + "\n")

  /** Minimal interpreter for a Python triple-quoted string literal — only handles the escape
    * sequences `tripleQuoted` produces (`\\` and `\"`). Sufficient to verify round-trips.
    */
  private def decodePythonStringLiteral(src: String): String =
    require(src.startsWith("\"\"\"") && src.endsWith("\"\"\""), s"not triple-quoted: $src")
    val body = src.drop(3).dropRight(3)
    val out  = new StringBuilder
    var i    = 0
    while i < body.length do
      val c = body.charAt(i)
      if c == '\\' && i + 1 < body.length then
        val next = body.charAt(i + 1)
        out.append(next)
        i += 2
      else
        out.append(c)
        i += 1
    out.toString
