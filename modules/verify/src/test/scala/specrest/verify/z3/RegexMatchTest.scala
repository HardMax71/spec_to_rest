package specrest.verify.z3

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite
import specrest.verify.CheckStatus
import specrest.verify.VerificationConfig

class RegexMatchTest extends CatsEffectSuite:

  private val backend: Resource[IO, WasmBackend] = WasmBackend.make

  private def emptyArtifact: TranslatorArtifact =
    TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, hasPostState = false)

  private def membershipStatus(value: String, pattern: String): IO[CheckStatus] =
    val re = RegexParser.parse(pattern).getOrElse(fail(s"pattern did not parse: $pattern"))
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("s", Nil, Z3Sort.Str)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("s", Nil), Z3Expr.StrLit(value)),
        Z3Expr.InRe(Z3Expr.App("s", Nil), re)
      ),
      artifact = emptyArtifact
    )
    backend.use(_.check(script, VerificationConfig.Default).flatMap {
      case Right(result) => IO.pure(result.status)
      case Left(err)     => IO.raiseError(new AssertionError(s"backend check failed: $err"))
    })

  private val alnum: Option[Z3Regex] =
    Some(
      Z3Regex.Plus(
        Z3Regex.Union(List(
          Z3Regex.Range('a', 'z'),
          Z3Regex.Range('A', 'Z'),
          Z3Regex.Range('0', '9')
        ))
      )
    )

  test("parser lowers an alphanumeric class + quantifier"):
    IO(RegexParser.parse("/^[a-zA-Z0-9]+$/")).map(r => assertEquals(r, alnum))

  test("parser handles a negated class, escape and concatenation"):
    IO(RegexParser.parse("/^[^@]+@[^@]+\\.[^@]+$/").isDefined).map(d => assertEquals(d, true))

  List(
    "a{2,3}"  -> "bounded repetition",
    "(?:ab)"  -> "non-capturing group",
    "(?=ab)c" -> "lookahead"
  ).foreach: (pattern, label) =>
    test(s"parser falls back to None on unsupported feature: $label"):
      IO(RegexParser.parse(pattern)).map(r => assertEquals(r, None))

  List(
    ("abc123", "/^[a-zA-Z0-9]+$/", CheckStatus.Sat),
    ("ab c", "/^[a-zA-Z0-9]+$/", CheckStatus.Unsat),
    ("a@b.co", "/^[^@]+@[^@]+\\.[^@]+$/", CheckStatus.Sat),
    ("not-an-email", "/^[^@]+@[^@]+\\.[^@]+$/", CheckStatus.Unsat)
  ).foreach: (value, pattern, expected) =>
    test(s"str.in_re: '$value' against $pattern is $expected"):
      membershipStatus(value, pattern).map(s => assertEquals(s, expected))
