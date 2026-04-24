package specrest.convention.testutil

import cats.effect.IO
import specrest.ir.ServiceIR
import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Paths

object SpecFixtures:

  def loadIR(name: String): IO[ServiceIR] =
    IO.blocking(Files.readString(Paths.get(s"fixtures/spec/$name.spec")))
      .flatMap(buildFromSource(name, _))

  def buildFromSource(label: String, source: String): IO[ServiceIR] =
    Parse.parseSpec(source).flatMap:
      case Left(err) =>
        IO.raiseError(new AssertionError(s"parse errors for $label: ${err.errors}"))
      case Right(parsed) =>
        Builder.buildIR(parsed.tree).flatMap:
          case Left(err) =>
            IO.raiseError(new AssertionError(s"build error for $label: ${err.message}"))
          case Right(ir) => IO.pure(ir)
