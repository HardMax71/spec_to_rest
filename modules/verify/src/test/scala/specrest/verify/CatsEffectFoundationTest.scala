package specrest.verify

import cats.effect.IO
import munit.CatsEffectSuite

class CatsEffectFoundationTest extends CatsEffectSuite:

  test("IO.pure lifts and runs a value through the munit-cats-effect runtime"):
    IO.pure(42).assertEquals(42)
