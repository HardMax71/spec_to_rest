package specrest.verify.audit

import munit.FunSuite
import specrest.ir.*
import specrest.verify.cert.{Emit, VerifiedSubset}
import java.nio.file.{Files, Path, Paths}
import java.security.MessageDigest

class ProofDriftAuditTest extends FunSuite:

  /** The exact set of `Expr`-shape names that `proofs/lean/SpecRest/Translate.lean` claims to
    * cover. Source of truth: `Translate.lean`'s `def translate : Expr → SmtTerm` arms. Update both
    * this set and `Translate.lean` atomically when the verified subset changes.
    */
  private val leanCoveredShapes: Set[String] = Set(
    "BoolLit",
    "IntLit",
    "Identifier",
    "UnaryOp.Not",
    "UnaryOp.Negate",
    "BinaryOp.And",
    "BinaryOp.Or",
    "BinaryOp.Implies",
    "BinaryOp.Iff",
    "BinaryOp.Eq",
    "BinaryOp.Neq",
    "BinaryOp.Lt",
    "BinaryOp.Le",
    "BinaryOp.Gt",
    "BinaryOp.Ge",
    "BinaryOp.In",
    "BinaryOp.Add",
    "BinaryOp.Sub",
    "BinaryOp.Mul",
    "BinaryOp.Div",
    "Quantifier.All",
    "Let",
    "EnumAccess"
  )

  private val repoRoot: Path = locateRepoRoot()

  /** Determine repo root by walking upward until we find `proofs/lean/lakefile.toml`. The test may
    * run from `modules/verify/` or from the repo root depending on sbt invocation.
    */
  private def locateRepoRoot(): Path =
    @scala.annotation.tailrec
    def walk(p: Option[Path]): Path = p match
      case None => Paths.get("").toAbsolutePath
      case Some(dir) =>
        if Files.exists(dir.resolve("proofs/lean/lakefile.toml")) then dir
        else walk(Option(dir.getParent))
    walk(Some(Paths.get("").toAbsolutePath))

  test("A1: VerifiedSubset.classify accepts exactly the shapes Translate.lean covers"):
    val classifierAccepts = CanonicalProbes.allProbes
      .filter((_, expr) => VerifiedSubset.isInSubset(expr))
      .map(_._1)
      .toSet
    val classifierOnly = classifierAccepts -- leanCoveredShapes
    val leanOnly       = leanCoveredShapes -- classifierAccepts
    val ok             = classifierOnly.isEmpty && leanOnly.isEmpty
    assert(
      ok,
      clue = s"""
        |Drift between VerifiedSubset.classify and Translate.lean.
        |  Classifier accepts but Lean lacks: $classifierOnly
        |  Lean covers but classifier rejects: $leanOnly
        |
        |Fix:
        |  - If a shape was added on the Scala side, extend SpecRest/Translate.lean and
        |    add it to leanCoveredShapes here.
        |  - If a shape moved out of subset on the Scala side, drop it from
        |    SpecRest/Translate.lean and from leanCoveredShapes.
        |""".stripMargin
    )

  test("A4: classifier-accepted probes never produce UNRENDERABLE in renderExpr"):
    val proofsLean = repoRoot.resolve("proofs/lean").toString
    CanonicalProbes.allProbes.foreach: (shape, expr) =>
      if VerifiedSubset.isInSubset(expr) then
        val ir = ServiceIR(
          name = s"Probe_${shape.replace('.', '_')}",
          enums = List(EnumDecl(name = "Color", values = List("red", "green"))),
          state = Some(
            StateDecl(fields =
              List(
                StateFieldDecl(name = "v", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "n", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "x", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "a", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "b", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "rel", typeExpr = TypeExpr.NamedType("Int"))
              )
            )
          ),
          invariants = List(
            InvariantDecl(name = Some(s"probe_$shape"), expr = expr)
          )
        )
        val bundle = Emit.emit(ir, proofsLean)
        assert(
          !bundle.renderModule.contains("UNRENDERABLE"),
          clue = s"InSubset probe '$shape' produced UNRENDERABLE marker in rendered cert"
        )

  test(
    "A5: every leanCoveredShape has a canonical probe whose top-level case is a Scala Expr case"
  ):
    val scalaCases = SourceParsers.parseScalaEnumCases(
      repoRoot.resolve("modules/ir/src/main/scala/specrest/ir/Types.scala"),
      "Expr"
    )
    val probeMap = CanonicalProbes.allProbes.toMap
    leanCoveredShapes.foreach: shape =>
      val probe = probeMap.get(shape)
      assert(
        probe.isDefined,
        clue = s"leanCoveredShapes lists '$shape' but no canonical probe exists in CanonicalProbes"
      )
      val prefix = shape.takeWhile(_ != '.')
      assert(
        scalaCases.contains(prefix),
        clue = s"Probe '$shape' references Scala Expr case '$prefix' which Types.scala lacks"
      )

  test("A7: Cert.lean SHA-256 matches recorded fingerprint"):
    val certPath = repoRoot.resolve("proofs/lean/SpecRest/Cert.lean")
    val shaPath  = repoRoot.resolve("proofs/lean/.cert-sha")
    val recorded = Files.readString(shaPath).trim
    val actual   = sha256(Files.readAllBytes(certPath))
    assertEquals(
      actual,
      recorded,
      clue = s"""
        |Cert.lean changed without bumping the recorded fingerprint.
        |  Recorded SHA: $recorded
        |  Actual SHA:   $actual
        |
        |If the change is intentional:
        |  1. Audit any externally-emitted cert bundles for compatibility.
        |  2. Update proofs/lean/.cert-sha:  echo $actual > proofs/lean/.cert-sha
        |""".stripMargin
    )

  private def sha256(bytes: Array[Byte]): String =
    val md = MessageDigest.getInstance("SHA-256")
    md.digest(bytes).map("%02x".format(_)).mkString
