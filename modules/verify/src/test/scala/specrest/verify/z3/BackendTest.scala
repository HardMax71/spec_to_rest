package specrest.verify.z3

import specrest.parser.Builder
import specrest.parser.Parse
import specrest.verify.CheckStatus
import specrest.verify.VerificationConfig

import java.nio.file.Files
import java.nio.file.Paths

class BackendTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpecSync(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIRSync(parsed.tree).toOption.get

  private def emptyArtifact: TranslatorArtifact =
    TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, hasPostState = false)

  test("trivial empty script is sat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(Nil, Nil, Nil, emptyArtifact)
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("assertion (false) is unsat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(Nil, Nil, List(Z3Expr.BoolLit(false)), emptyArtifact)
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("simple integer assertion (>= 0) is sat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(
        sorts = Nil,
        funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
        assertions = List(
          Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(0))
        ),
        artifact = emptyArtifact
      )
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("contradictory ints are unsat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(
        sorts = Nil,
        funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
        assertions = List(
          Z3Expr.Cmp(CmpOp.Ge, Z3Expr.App("x", Nil), Z3Expr.IntLit(10)),
          Z3Expr.Cmp(CmpOp.Le, Z3Expr.App("x", Nil), Z3Expr.IntLit(5))
        ),
        artifact = emptyArtifact
      )
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("url_shortener invariants are sat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("url_shortener")
      val script = Translator.translateSync(ir).toOption.get
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("unsat_invariants fixture is unsat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("unsat_invariants")
      val script = Translator.translateSync(ir).toOption.get
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("safe_counter invariants are sat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("safe_counter")
      val script = Translator.translateSync(ir).toOption.get
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  private def intSet: Z3Sort = Z3Sort.SetOf(Z3Sort.Int)

  test("membership in set literal: x = 3 ∧ x ∈ {1,2,3} is sat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("membership in set literal: x = 4 ∧ x ∈ {1,2,3} is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("empty set membership is unsat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(
        sorts = Nil,
        funcs = List(Z3FunctionDecl("x", Nil, Z3Sort.Int)),
        assertions = List(
          Z3Expr.SetMember(Z3Expr.App("x", Nil), Z3Expr.EmptySet(Z3Sort.Int))
        ),
        artifact = emptyArtifact
      )
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set union membership: x ∈ A ∧ ¬(x ∈ A ∪ B) is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set intersection — forward: x ∈ A ∩ B ∧ ¬(x ∈ A ∧ x ∈ B) is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set intersection — converse: x ∈ A ∧ x ∈ B ∧ ¬(x ∈ A ∩ B) is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set difference: x ∈ A \\ B ∧ x ∈ B is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set subset: A ⊆ B ∧ x ∈ A ∧ ¬(x ∈ B) is unsat"):
    val backend = WasmBackend()
    try
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
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("set_ops fixture invariants are sat at the IR level"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("set_ops")
      val script = Translator.translateSync(ir).toOption.get
      val result = backend.checkSync(script, VerificationConfig.Default).toOption.get
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()
