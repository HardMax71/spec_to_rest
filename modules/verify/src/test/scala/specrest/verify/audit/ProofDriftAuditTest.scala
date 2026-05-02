package specrest.verify.audit

import munit.FunSuite
import specrest.ir.*
import specrest.verify.cert.Emit
import specrest.verify.cert.VerifiedSubset

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
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
    "BinaryOp.NotIn",
    "BinaryOp.Add",
    "BinaryOp.Sub",
    "BinaryOp.Mul",
    "BinaryOp.Div",
    "Quantifier.All",
    "Quantifier.Some",
    "Quantifier.No",
    "Quantifier.Exists",
    "Let",
    "EnumAccess",
    "Prime",
    "Pre",
    "UnaryOp.Cardinality",
    "Index"
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

  /** Probes that depend on state-relation values for evaluation. The demo state used by `Emit.emit`
    * has `relations = Nil`, so eval of these probes returns `none` and the cert emitter falls back
    * to a trivial stub even after Eq-wrapping. The renderer is still exercised (UNRENDERABLE check
    * still fires), but `certifiedChecks ≥ 1` cannot hold without populating demo relations.
    */
  private val needsStateRelations: Set[String] = Set(
    "UnaryOp.Cardinality",
    "BinaryOp.In",
    "BinaryOp.NotIn",
    "Index"
  )

  test("A4: classifier-accepted probes never produce UNRENDERABLE in renderExpr"):
    val proofsLean = repoRoot.resolve("proofs/lean").toString
    CanonicalProbes.allProbes.foreach: (shape, expr) =>
      if VerifiedSubset.isInSubset(expr) then
        // Wrap the probe in `(probe == probe)` so the invariant body always has Bool type.
        // Without this wrapping, atom-shape probes (`IntLit`, `Identifier`, `Let`, `arith`,
        // `Cardinality`, `EnumAccess`, `Prime`, `Pre`) produce non-Bool eval results and
        // the cert emitter falls back to a trivial stub — bypassing renderExpr entirely
        // (cubic P2). The Eq wrapper exercises the renderer recursively on the probe.
        val invExpr = Expr.BinaryOp(BinOp.Eq, expr, expr)
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
                StateFieldDecl(name = "rel", typeExpr = TypeExpr.NamedType("Int")),
                StateFieldDecl(name = "count", typeExpr = TypeExpr.NamedType("Int"))
              )
            )
          ),
          invariants = List(
            InvariantDecl(name = Some(s"probe_$shape"), expr = invExpr)
          )
        )
        val bundle = Emit.emit(ir, proofsLean)
        assert(
          !bundle.renderModule.contains("UNRENDERABLE"),
          clue = s"InSubset probe '$shape' produced UNRENDERABLE marker in rendered cert"
        )
        // Positive evidence: the wrapped invariant is Bool-typed, so the cert emitter
        // must produce a cert_decide obligation (not a trivial stub). This catches the
        // case where renderExpr silently falls back to a stub even for in-subset probes.
        // Probes that depend on state-relation values are exempt — see needsStateRelations.
        if !needsStateRelations.contains(shape) then
          assert(
            bundle.summary.certifiedChecks >= 1,
            clue = s"InSubset probe '$shape' did not produce any cert_decide obligation; " +
              "the renderer may have silently fallen back to a trivial stub. " +
              s"Bundle summary: ${bundle.summary}"
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

  /** A2 probe: for each Lean-side shape that's in the verified subset, there must be a
    * corresponding handler in the production Scala translator. We verify by substring presence of
    * the operator literal in `Translator.scala` — coarser than line-range pinning but robust to
    * refactors that move code without removing handlers.
    */
  private val translatorRequiredLiterals: List[(String, String)] = List(
    "BoolLit"             -> "Expr.BoolLit",
    "IntLit"              -> "Expr.IntLit",
    "Identifier"          -> "Expr.Identifier",
    "UnaryOp.Not"         -> "UnOp.Not",
    "UnaryOp.Negate"      -> "UnOp.Negate",
    "UnaryOp.Cardinality" -> "UnOp.Cardinality",
    "BinaryOp.And"        -> "BinOp.And",
    "BinaryOp.Or"         -> "BinOp.Or",
    "BinaryOp.Implies"    -> "BinOp.Implies",
    "BinaryOp.Iff"        -> "BinOp.Iff",
    "BinaryOp.Eq"         -> "BinOp.Eq",
    "BinaryOp.Neq"        -> "BinOp.Neq",
    "BinaryOp.Lt"         -> "BinOp.Lt",
    "BinaryOp.Le"         -> "BinOp.Le",
    "BinaryOp.Gt"         -> "BinOp.Gt",
    "BinaryOp.Ge"         -> "BinOp.Ge",
    "BinaryOp.In"         -> "BinOp.In",
    "BinaryOp.NotIn"      -> "BinOp.NotIn",
    "BinaryOp.Add"        -> "BinOp.Add",
    "BinaryOp.Sub"        -> "BinOp.Sub",
    "BinaryOp.Mul"        -> "BinOp.Mul",
    "BinaryOp.Div"        -> "BinOp.Div",
    "Quantifier.All"      -> "QuantKind.All",
    "Quantifier.Some"     -> "QuantKind.Some",
    "Quantifier.No"       -> "QuantKind.No",
    "Quantifier.Exists"   -> "QuantKind.Exists",
    "Let"                 -> "Expr.Let",
    "EnumAccess"          -> "Expr.EnumAccess",
    "Prime"               -> "Expr.Prime",
    "Pre"                 -> "Expr.Pre",
    "Index"               -> "Expr.Index"
  )

  test("A2: every leanCoveredShape's operator literal is present in z3.Translator.scala"):
    val translatorPath = repoRoot.resolve(
      "modules/verify/src/main/scala/specrest/verify/z3/Translator.scala"
    )
    val src = Files.readString(translatorPath)
    translatorRequiredLiterals.foreach: (shape, literal) =>
      if leanCoveredShapes.contains(shape) then
        assert(
          src.contains(literal),
          clue = s"""
            |z3.Translator.scala no longer references '$literal' (lean-covered shape '$shape').
            |Either the production translator dropped support, or the literal was renamed.
            |Audit Translator.scala and update translatorRequiredLiterals if intentional.
            |""".stripMargin
        )

  /** A3 probe: hardcoded mapping from STATUS-row case names to expected Soundness.lean theorem
    * substrings. The check ensures STATUS doesn't claim a row is `sound` while the corresponding
    * theorem has been renamed or removed.
    */
  private val soundRowToTheorem: List[(String, String)] = List(
    "BoolLit"                  -> "soundness_boolLit",
    "IntLit"                   -> "soundness_intLit",
    "Identifier (env-hit)"     -> "soundness_ident_local",
    "Identifier (state)"       -> "soundness_ident_state",
    "BinaryOp(And)"            -> "soundness_boolBin_and_bools",
    "BinaryOp(Or)"             -> "soundness_boolBin_or_bools",
    "BinaryOp(Implies)"        -> "soundness_boolBin_implies_bools",
    "BinaryOp(Iff)"            -> "soundness_boolBin_iff_bools",
    "BinaryOp(Eq)"             -> "soundness_cmp_eq_vals",
    "BinaryOp(Neq)"            -> "soundness_cmp_neq_vals",
    "BinaryOp(Lt)"             -> "soundness_cmp_lt_ints",
    "BinaryOp(Le)"             -> "soundness_cmp_le_ints",
    "BinaryOp(Gt)"             -> "soundness_cmp_gt_ints",
    "BinaryOp(Ge)"             -> "soundness_cmp_ge_ints",
    "BinaryOp(In)"             -> "soundness_member_resolved",
    "UnaryOp(Not)"             -> "soundness_unNot_bool",
    "UnaryOp(Negate)"          -> "soundness_unNeg_int",
    "Quantifier(All)"          -> "soundness_forallEnum_known",
    "Quantifier(All over rel)" -> "soundness_forallRel_known",
    "Let"                      -> "soundness_letIn",
    "EnumAccess"               -> "soundness_enumAccess_known",
    "BinaryOp(Add)"            -> "soundness_arith_add_ints",
    "BinaryOp(Sub)"            -> "soundness_arith_sub_ints",
    "BinaryOp(Mul)"            -> "soundness_arith_mul_ints",
    "BinaryOp(Div nonzero)"    -> "soundness_arith_div_ints_nonZero",
    "BinaryOp(Div zero)"       -> "soundness_arith_div_ints_zero",
    // M_L.4.b/c branches don't have dedicated soundness_<arm> theorems — they're inline
    // in the universal `soundness` theorem. We pin raw substrings (not theorem names)
    // for these so a rename or removal in Soundness.lean still red-fires A3.
    "UnaryOp(Cardinality)" -> "correlateModel_lookupRel s st relName dom hDom",
    "Prime"                -> "| prime e ih =>",
    "Pre"                  -> "| pre e ih =>",
    "Index"                -> "| indexRel relName key ihKey =>",
    "Universal soundness"  -> "theorem soundness"
  )

  test("A3: every claimed Soundness.lean fingerprint exists in the file"):
    val soundnessSrc = Files.readString(
      repoRoot.resolve("proofs/lean/SpecRest/Soundness.lean")
    )
    soundRowToTheorem.foreach: (statusRow, fingerprint) =>
      // Entries that look like a theorem stem (start with `theorem ` or `soundness_` etc.)
      // get the `theorem ` prefix prepended; raw substrings (containing spaces or `|`) are
      // matched verbatim so we can pin universal-theorem match-arms.
      val needle =
        if fingerprint.startsWith("theorem ") || fingerprint.contains(" ") ||
          fingerprint.startsWith("|")
        then fingerprint
        else s"theorem $fingerprint"
      assert(
        soundnessSrc.contains(needle),
        clue = s"""
          |Soundness.lean missing expected fingerprint for STATUS row '$statusRow'.
          |  Expected substring: $needle
          |
          |Either the theorem/match-arm was renamed/removed (update soundRowToTheorem here),
          |or STATUS.md falsely claims this row is sound (downgrade STATUS row).
          |""".stripMargin
      )

  test(
    "A8: lakefile version bumped if any proofs/lean/SpecRest/*.lean changed since .last-release-sha"
  ):
    val lastShaPath = repoRoot.resolve("proofs/lean/.last-release-sha")
    if !Files.exists(lastShaPath) then
      fail(
        "A8: proofs/lean/.last-release-sha missing. Either restore the file or remove A8. " +
          "Silent skipping is no longer permitted."
      )
    val lastSha = Files.readString(lastShaPath).trim
    tryGit(Seq("show", s"$lastSha:proofs/lean/lakefile.toml")) match
      case None =>
        // Shallow clone or unreachable SHA — log a clear warning and pass; CI must use a
        // deep enough fetch (actions/checkout fetch-depth: 0) to resolve the baseline. The
        // warning is still visible in the test runner output so this can be diagnosed.
        println(
          s"[A8 SKIP] git could not resolve baseline SHA $lastSha. " +
            "This usually means a shallow clone. Set fetch-depth: 0 in CI to enforce A8."
        )
      case Some(historicalLakefile) =>
        val currentLakefile = Files.readString(repoRoot.resolve("proofs/lean/lakefile.toml"))
        val currentVersion  = parseLakefileVersion(currentLakefile)
        val historicalVer   = parseLakefileVersion(historicalLakefile)
        val changes = tryGit(
          Seq("diff", "--name-only", s"$lastSha..HEAD", "--", "proofs/lean/SpecRest/")
        ).getOrElse("")
        val leanChanged = changes.linesIterator.exists(_.endsWith(".lean"))
        if leanChanged then
          assert(
            currentVersion != historicalVer,
            clue = s"""
                |proofs/lean/SpecRest/*.lean changed since $lastSha but the lakefile version is
                |unchanged.
                |  Recorded baseline SHA: $lastSha
                |  Version at baseline:   $historicalVer
                |  Current version:       $currentVersion
                |
                |Fix: bump proofs/lean/lakefile.toml version AND update .last-release-sha to the
                |bump commit's SHA.
                |""".stripMargin
          )

  /** Parse the `version = "X.Y.Z"` field from a lakefile.toml file. Returns the version string, or
    * empty string if not found.
    */
  private def parseLakefileVersion(toml: String): String =
    val pattern = """(?m)^\s*version\s*=\s*"([^"]+)"""".r
    pattern.findFirstMatchIn(toml).map(_.group(1)).getOrElse("")

  /** Run a git command from `repoRoot` and return stdout, or None on any failure (shallow clone,
    * missing SHA, git not on PATH).
    */
  private def tryGit(args: Seq[String]): Option[String] =
    try
      val pb = new ProcessBuilder(("git" +: args)*)
      pb.directory(repoRoot.toFile)
      pb.redirectErrorStream(true)
      val proc   = pb.start()
      val output = scala.io.Source.fromInputStream(proc.getInputStream).mkString
      val rc     = proc.waitFor()
      if rc == 0 then Some(output) else None
    catch case _: Throwable => None

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
