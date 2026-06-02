package specrest.verify.z3

import cats.effect.IO
import cats.effect.Resource
import munit.CatsEffectSuite
import specrest.verify.CheckStatus
import specrest.verify.VerificationConfig
import specrest.verify.testutil.SpecFixtures

class BackendTest extends CatsEffectSuite:

  private val wasmBackend: Resource[IO, WasmBackend] = WasmBackend.make

  private def emptyArtifact: TranslatorArtifact =
    TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, hasPostState = false)

  private def intSet: Z3Sort = Z3Sort.SetOf(Z3Sort.Int)

  private def runScript(script: Z3Script): IO[SmokeCheckResult] =
    wasmBackend.use: backend =>
      backend.check(script, VerificationConfig.Default).map(_.toOption.get)

  private def runIR(name: String): IO[SmokeCheckResult] =
    for
      ir      <- SpecFixtures.loadIR(name)
      scriptE <- Translator.translate(ir)
      script   = scriptE.toOption.get
      result  <- runScript(script)
    yield result

  test("trivial empty script is sat"):
    runScript(Z3Script(Nil, Nil, Nil, emptyArtifact)).map: result =>
      assertEquals(result.status, CheckStatus.Sat)

  test("assertion (false) is unsat"):
    runScript(Z3Script(Nil, Nil, List(Z3Expr.BoolLit(false)), emptyArtifact)).map: result =>
      assertEquals(result.status, CheckStatus.Unsat)

  test("simple integer assertion (>= 0) is sat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(BigInt(0)))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Sat))

  test("contradictory ints are unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(BigInt(10))),
        Z3Expr.Cmp(CmpOp.Le, Z3Expr.App("x", Nil), Z3Expr.IntLit(BigInt(5)))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("url_shortener invariants are sat"):
    runIR("url_shortener").map(r => assertEquals(r.status, CheckStatus.Sat))

  test("unsat_invariants fixture is unsat"):
    runIR("unsat_invariants").map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("safe_counter invariants are sat"):
    runIR("safe_counter").map(r => assertEquals(r.status, CheckStatus.Sat))

  test("membership in set literal: x = 3 ∧ x ∈ {1,2,3} is sat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.IntLit(BigInt(3))),
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetLit(
            Z3Sort.Int,
            List(Z3Expr.IntLit(BigInt(1)), Z3Expr.IntLit(BigInt(2)), Z3Expr.IntLit(BigInt(3)))
          )
        )
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Sat))

  test("membership in set literal: x = 4 ∧ x ∈ {1,2,3} is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.IntLit(BigInt(4))),
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetLit(
            Z3Sort.Int,
            List(Z3Expr.IntLit(BigInt(1)), Z3Expr.IntLit(BigInt(2)), Z3Expr.IntLit(BigInt(3)))
          )
        )
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("empty set membership is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.EmptySet(Z3Sort.Int))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set union membership: x ∈ A ∧ ¬(x ∈ A ∪ B) is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(
        Z3FunctionDecl("x", Nil, Z3Sort.Int),
        Z3FunctionDecl("A", Nil, intSet),
        Z3FunctionDecl("B", Nil, intSet)
      ),
      assertions = List(
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("A", Nil)),
        Z3Expr.Not(
          Z3Expr.SetMember(
            Z3Expr.App("x", Nil),
            Z3Expr.SetBinOp(SetOpKind.Union, Z3Expr.App("A", Nil), Z3Expr.App("B", Nil))
          )
        )
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set intersection — forward: x ∈ A ∩ B ∧ ¬(x ∈ A ∧ x ∈ B) is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(
        Z3FunctionDecl("x", Nil, Z3Sort.Int),
        Z3FunctionDecl("A", Nil, intSet),
        Z3FunctionDecl("B", Nil, intSet)
      ),
      assertions = List(
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetBinOp(SetOpKind.Intersect, Z3Expr.App("A", Nil), Z3Expr.App("B", Nil))
        ),
        Z3Expr.Not(
          Z3Expr.And(List(
            Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("A", Nil)),
            Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("B", Nil))
          ))
        )
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set intersection — converse: x ∈ A ∧ x ∈ B ∧ ¬(x ∈ A ∩ B) is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(
        Z3FunctionDecl("x", Nil, Z3Sort.Int),
        Z3FunctionDecl("A", Nil, intSet),
        Z3FunctionDecl("B", Nil, intSet)
      ),
      assertions = List(
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("A", Nil)),
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("B", Nil)),
        Z3Expr.Not(
          Z3Expr.SetMember(
            Z3Expr.App("x", Nil),
            Z3Expr.SetBinOp(SetOpKind.Intersect, Z3Expr.App("A", Nil), Z3Expr.App("B", Nil))
          )
        )
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set difference: x ∈ A \\ B ∧ x ∈ B is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(
        Z3FunctionDecl("x", Nil, Z3Sort.Int),
        Z3FunctionDecl("A", Nil, intSet),
        Z3FunctionDecl("B", Nil, intSet)
      ),
      assertions = List(
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetBinOp(SetOpKind.Diff, Z3Expr.App("A", Nil), Z3Expr.App("B", Nil))
        ),
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("B", Nil))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set subset: A ⊆ B ∧ x ∈ A ∧ ¬(x ∈ B) is unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(
        Z3FunctionDecl("x", Nil, Z3Sort.Int),
        Z3FunctionDecl("A", Nil, intSet),
        Z3FunctionDecl("B", Nil, intSet)
      ),
      assertions = List(
        Z3Expr.SetBinOp(SetOpKind.Subset, Z3Expr.App("A", Nil), Z3Expr.App("B", Nil)),
        Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("A", Nil)),
        Z3Expr.Not(Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.App("B", Nil)))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Unsat))

  test("set_ops fixture invariants are sat at the IR level"):
    runIR("set_ops").map(r => assertEquals(r.status, CheckStatus.Sat))

  List(
    ("true guard selects then-branch", Z3Expr.BoolLit(true), BigInt(5), CheckStatus.Sat),
    ("false guard selects else-branch", Z3Expr.BoolLit(false), BigInt(3), CheckStatus.Sat),
    ("true guard does not select else-branch", Z3Expr.BoolLit(true), BigInt(3), CheckStatus.Unsat)
  ).foreach: (name, guard, equalsTo, expected) =>
    test(s"ite renders and solves: $name"):
      val ite = Z3Expr.Ite(guard, Z3Expr.IntLit(BigInt(5)), Z3Expr.IntLit(BigInt(3)))
      val script = Z3Script(
        sorts = Nil,
        funcs = Nil,
        assertions = List(Z3Expr.Cmp(CmpOp.Eq, ite, Z3Expr.IntLit(equalsTo))),
        artifact = emptyArtifact
      )
      runScript(script).map(r => assertEquals(r.status, expected))

  private def optInt: Z3Sort = Z3Sort.OptionOf(Z3Sort.Int)

  List(
    (
      "some(5) is distinct from none (sat)",
      List(
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.OptSome(Z3Expr.IntLit(BigInt(5)))),
        Z3Expr.Not(Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.OptNone(Z3Sort.Int)))
      ),
      CheckStatus.Sat
    ),
    (
      "x = none and x = some(5) is unsat",
      List(
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.OptNone(Z3Sort.Int)),
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.OptSome(Z3Expr.IntLit(BigInt(5))))
      ),
      CheckStatus.Unsat
    )
  ).foreach: (name, assertions, expected) =>
    test(s"option datatype renders and solves: $name"):
      val script = Z3Script(
        sorts = Nil,
        funcs = List(Z3FunctionDecl("x", Nil, optInt)),
        assertions = assertions,
        artifact = emptyArtifact
      )
      runScript(script).map(r => assertEquals(r.status, expected))
