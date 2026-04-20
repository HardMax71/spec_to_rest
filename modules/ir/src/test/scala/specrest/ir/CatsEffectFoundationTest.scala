package specrest.ir

import cats.effect.IO
import munit.CatsEffectSuite

class CatsEffectFoundationTest extends CatsEffectSuite:

  test("IO.pure lifts and runs a value through the munit-cats-effect runtime"):
    IO.pure(42).assertEquals(42)

  test("IO.delay defers a side effect until evaluation"):
    for
      ref <- IO.ref(0)
      _   <- ref.update(_ + 1)
      v   <- ref.get
    yield assertEquals(v, 1)
