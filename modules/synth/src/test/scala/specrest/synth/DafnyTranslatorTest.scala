package specrest.synth

import cats.effect.IO
import munit.CatsEffectSuite

class DafnyTranslatorTest extends CatsEffectSuite:

  private val sampleSource =
    """class ServiceState { var count: int }
      |
      |predicate Inv(s: ServiceState) reads s { s.count >= 0 }
      |
      |method Inc(s: ServiceState)
      |  modifies s
      |  requires Inv(s)
      |  ensures s.count == old(s.count) + 1
      |  ensures Inv(s)
      |{
      |  s.count := s.count + 1;
      |}
      |""".stripMargin

  test("MockDafnyTranslator returns staged file map"):
    val canned =
      MockDafnyTranslator.success(
        TargetLanguage.Python,
        Map(
          "module_.py"          -> "# verified body lives here\n",
          "_dafny/__init__.py"  -> "# runtime\n",
          "System_/__init__.py" -> "# system\n",
          "__main__.py"         -> "# entry\n"
        )
      )
    for
      tx       <- MockDafnyTranslator.of(List(Right(canned)))
      result   <- tx.translate(sampleSource, TargetLanguage.Python, timeoutSec = 60)
      recorded <- tx.calls
    yield
      val out = result.getOrElse(fail("expected translation success"))
      assertEquals(out.target, TargetLanguage.Python)
      assertEquals(
        out.files.keySet,
        Set("module_.py", "_dafny/__init__.py", "System_/__init__.py", "__main__.py")
      )
      assertEquals(recorded, List(sampleSource))

  test("MockDafnyTranslator surfaces errors verbatim"):
    for
      tx     <- MockDafnyTranslator.of(List(Left("dafny: parse failure at line 4")))
      result <- tx.translate(sampleSource, TargetLanguage.Python, timeoutSec = 60)
    yield assertEquals(result, Left("dafny: parse failure at line 4"))

  test("MockDafnyTranslator exhausts after the planned calls"):
    for
      tx <- MockDafnyTranslator.of(List(Right(MockDafnyTranslator.success(
              TargetLanguage.Python,
              Map.empty
            ))))
      _  <- tx.translate(sampleSource, TargetLanguage.Python, 60)
      r2 <- tx.translate(sampleSource, TargetLanguage.Python, 60)
    yield assert(r2.isLeft, s"second call should be Left, got $r2")

  private def dafnyOnPath: IO[Boolean] =
    DafnyCli.resolveBinary(None).map(_.isRight)

  test("DafnyTranslateCli emits module_.py when dafny is on PATH"):
    dafnyOnPath.flatMap:
      case false => IO.pure(())
      case true =>
        DafnyCli.resolveBinary(None).flatMap:
          case Left(msg) => IO(fail(s"unexpected: $msg"))
          case Right(bin) =>
            DafnyTranslateCli.make(bin).use: tx =>
              tx.translate(sampleSource, TargetLanguage.Python, 60).map:
                case Left(err) => fail(s"translate failed: $err")
                case Right(out) =>
                  assert(
                    out.files.contains("module_.py"),
                    s"missing module_.py in ${out.files.keys}"
                  )
                  assert(
                    out.files("module_.py").contains("class ServiceState"),
                    out.files("module_.py").take(200)
                  )
                  assert(
                    out.files.contains("_dafny/__init__.py"),
                    s"missing _dafny runtime in ${out.files.keys}"
                  )
