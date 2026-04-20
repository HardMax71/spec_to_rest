package specrest.cli

import cats.effect.IO
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import munit.CatsEffectSuite

class CatsEffectFoundationTest extends CatsEffectSuite:

  test("IO.pure lifts and runs a value through the munit-cats-effect runtime"):
    IO.pure(42).assertEquals(42)

  test("decline-effect CommandIOApp is on the classpath and composes with decline Opts"):
    val dummy = new CommandIOApp(name = "dummy", header = "dummy"):
      override def main: Opts[IO[cats.effect.ExitCode]] =
        Opts(IO.pure(cats.effect.ExitCode.Success))
    IO.pure(dummy).map(_ => ()).assertEquals(())
