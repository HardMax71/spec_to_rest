package specrest.verify

import cats.effect.IO
import cats.effect.Ref
import munit.CatsEffectSuite
import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.alloy.AlloyBackend
import specrest.verify.certificates.DumpSink
import specrest.verify.z3.WasmBackend

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class ResourceLifecycleTest extends CatsEffectSuite:

  private val releaseCases: List[(String, Ref[IO, Boolean] => IO[Unit])] = List(
    "WasmBackend.make runs its release action after use" ->
      (released =>
        WasmBackend
          .make(IO.blocking(WasmBackend())): backend =>
            released.set(true) >> IO.blocking(backend.close())
          .use(_ => IO.unit)
      ),
    "AlloyBackend.make runs its release action after use" ->
      (released =>
        AlloyBackend
          .make(IO.blocking(new AlloyBackend)): backend =>
            released.set(true) >> IO.blocking(backend.close())
          .use(_ => IO.unit)
      ),
    "DumpSink.openResource runs its release action after use" ->
      (released =>
        tempDirResource("dump-resource-release-")
          .flatMap: dir =>
            DumpSink.openResource(openSinkIO(dir)): dump =>
              released.set(true) >> IO.blocking(dump.close())
          .use: sink =>
            IO(assert(Files.isDirectory(sink.dir)))
      )
  )

  releaseCases.foreach: (name, runCase) =>
    test(name):
      assertReleased(runCase)

  test("runConsistencyChecksIO acquires and uses managed backends"):
    val ir = buildIR("safe_counter")
    Consistency
      .runConsistencyChecksIO(ir, VerificationConfig.Default)
      .map(report => assertEquals(report.ok, true))

  private def assertReleased(runCase: Ref[IO, Boolean] => IO[Unit]): IO[Unit] =
    for
      released    <- Ref[IO].of(false)
      _           <- runCase(released)
      wasReleased <- released.get
    yield assertEquals(wasReleased, true)

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree).toOption.get

  private def tempDirResource(prefix: String): cats.effect.Resource[IO, Path] =
    cats.effect.Resource.make(IO.blocking(Files.createTempDirectory(prefix))): dir =>
      IO.blocking(deleteRecursive(dir))

  private def openSinkIO(dir: Path): IO[DumpSink] =
    IO.defer:
      DumpSink.open(dir) match
        case Right(sink) => IO.pure(sink)
        case Left(err)   => IO.raiseError(RuntimeException(err.message))

  private def deleteRecursive(path: Path): Unit =
    if Files.isDirectory(path) then
      val stream = Files.list(path)
      try
        val iter = stream.iterator()
        while iter.hasNext do deleteRecursive(iter.next())
      finally stream.close()
    val _ = Files.deleteIfExists(path)
