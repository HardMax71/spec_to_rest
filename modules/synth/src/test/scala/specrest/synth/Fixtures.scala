package specrest.synth

import cats.effect.IO
import specrest.convention.Classify
import specrest.convention.OperationClassification
import specrest.convention.dafny.DafnyMethodHeader
import specrest.convention.dafny.DafnyOutput
import specrest.convention.dafny.Generator as DafnyGenerator
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Paths

object Fixtures:

  def loadIR(name: String): IO[ServiceIRFull] =
    IO.blocking(Files.readString(Paths.get(s"fixtures/spec/$name.spec")))
      .flatMap: src =>
        Parse.parseSpec(src).flatMap:
          case Left(e) => IO.raiseError(new AssertionError(s"parse failed: $e"))
          case Right(parsed) =>
            Builder.buildIR(parsed.tree).flatMap:
              case Left(e)   => IO.raiseError(new AssertionError(s"build failed: ${e.message}"))
              case Right(ir) => IO.pure(ir)

  def loadHeader(
      specName: String,
      opName: String
  ): IO[(OperationClassification, DafnyMethodHeader, String)] =
    loadIR(specName).map: ir =>
      val classifications = Classify.classifyOperations(ir)
      val c = classifications
        .find(_.operationName == opName)
        .getOrElse(throw new AssertionError(s"operation $opName not found"))
      val out: DafnyOutput =
        DafnyGenerator.generate(ir).toOption.getOrElse(throw new AssertionError("dafny gen failed"))
      val h = out.methods
        .find(_.name == opName)
        .getOrElse(throw new AssertionError(s"header $opName missing"))
      (c, h, out.text)
