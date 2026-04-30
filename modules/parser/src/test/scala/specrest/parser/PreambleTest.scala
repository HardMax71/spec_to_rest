package specrest.parser

import munit.CatsEffectSuite

class PreambleTest extends CatsEffectSuite:

  test("preamble loads cleanly and exposes the two built-in predicates"):
    val names = Preamble.predicates.map(_.name).toSet
    assertEquals(names, Set("isValidURI", "isValidEmail"))

  test("Preamble.load returns Right when the resource is well-formed"):
    Preamble.load() match
      case Right(preds) => assertEquals(preds.size, 2)
      case Left(err)    => fail(s"unexpected preamble load failure: ${err.getMessage}")

  test("PreambleLoadException is a typed RuntimeException subtype"):
    val ex: PreambleLoadException = PreambleLoadException("smoke")
    assertEquals(ex.getMessage, "smoke")
    assert(ex.isInstanceOf[RuntimeException])
