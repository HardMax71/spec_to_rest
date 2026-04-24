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
        Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(0))
      ),
      artifact = emptyArtifact
    )
    runScript(script).map(r => assertEquals(r.status, CheckStatus.Sat))

  test("contradictory ints are unsat"):
    val script = Z3Script(
      sorts = Nil,
      funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
      assertions = List(
        Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(10)),
        Z3Expr.Cmp(CmpOp.Le, Z3Expr.App("x", Nil), Z3Expr.IntLit(5))
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
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.IntLit(3)),
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetLit(Z3Sort.Int, List(Z3Expr.IntLit(1), Z3Expr.IntLit(2), Z3Expr.IntLit(3)))
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
        Z3Expr.Cmp(CmpOp.Eq, Z3Expr.App("x", Nil), Z3Expr.IntLit(4)),
        Z3Expr.SetMember(
          Z3Expr.App("x", Nil),
          Z3Expr.SetLit(Z3Sort.Int, List(Z3Expr.IntLit(1), Z3Expr.IntLit(2), Z3Expr.IntLit(3)))
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
