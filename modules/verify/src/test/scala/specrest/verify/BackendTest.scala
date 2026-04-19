package specrest.verify

import specrest.parser.Builder
import specrest.parser.Parse

import java.nio.file.Files
import java.nio.file.Paths

class BackendTest extends munit.FunSuite:

  private def buildIR(name: String): specrest.ir.ServiceIR =
    val src    = Files.readString(Paths.get(s"fixtures/spec/$name.spec"))
    val parsed = Parse.parseSpec(src)
    assert(parsed.errors.isEmpty, s"parse errors for $name: ${parsed.errors}")
    Builder.buildIR(parsed.tree)

  private def emptyArtifact: TranslatorArtifact =
    TranslatorArtifact(Nil, Nil, Nil, Nil, Nil, hasPostState = false)

  test("trivial empty script is sat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(Nil, Nil, Nil, emptyArtifact)
      val result = backend.check(script, VerificationConfig.Default)
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("assertion (false) is unsat"):
    val backend = WasmBackend()
    try
      val script = Z3Script(Nil, Nil, List(Z3Expr.BoolLit(false)), emptyArtifact)
      val result = backend.check(script, VerificationConfig.Default)
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
      val result = backend.check(script, VerificationConfig.Default)
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
      val result = backend.check(script, VerificationConfig.Default)
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("url_shortener invariants are sat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("url_shortener")
      val script = Translator.translate(ir)
      val result = backend.check(script, VerificationConfig.Default)
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()

  test("unsat_invariants fixture is unsat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("unsat_invariants")
      val script = Translator.translate(ir)
      val result = backend.check(script, VerificationConfig.Default)
      assertEquals(result.status, CheckStatus.Unsat)
    finally backend.close()

  test("safe_counter invariants are sat"):
    val backend = WasmBackend()
    try
      val ir     = buildIR("safe_counter")
      val script = Translator.translate(ir)
      val result = backend.check(script, VerificationConfig.Default)
      assertEquals(result.status, CheckStatus.Sat)
    finally backend.close()
