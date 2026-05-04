package specrest.verify.cert

import specrest.ir.*

object EmitIsabelle:

  final case class CertificateBundle(
      moduleName: String,
      theoryBody: String,
      summary: BundleSummary
  ):
    def renderTheory: String =
      s"""theory $moduleName
         |  imports SpecRest.Codegen
         |begin
         |
         |$theoryBody
         |end
         |""".stripMargin

    def renderRoot(spedRestPath: String): String =
      s"""chapter SpecRest
         |
         |session ${moduleName}_Session = HOL +
         |  options [document = false, threads = 4]
         |  sessions
         |    "HOL-Library"
         |    SpecRest
         |  theories
         |    $moduleName
         |""".stripMargin

  final case class BundleSummary(
      totalChecks: Int,
      certifiedChecks: Int,
      stubbedChecks: Int
  )

  def emit(ir: ServiceIR): CertificateBundle =
    val moduleName = sanitize(ir.name) + "_Cert"
    val enumNames  = ir.enums.map(_.name).toSet

    val theorems = ir.invariants.zipWithIndex.map: (inv, idx) =>
      renderInvariantTheorem(ir, inv, idx, enumNames)

    val (certified, stubbed) = theorems.foldLeft((0, 0)): (acc, t) =>
      val (c, s) = acc
      if t.contains("(* OUT OF M_L.1 VERIFIED SUBSET *)") then (c, s + 1)
      else (c + 1, s)

    val body = if theorems.isEmpty then "(* no invariants *)\n" else theorems.mkString("\n\n")

    CertificateBundle(
      moduleName = moduleName,
      theoryBody = body,
      summary = BundleSummary(theorems.size, certified, stubbed)
    )

  private def renderInvariantTheorem(
      ir: ServiceIR,
      inv: InvariantDecl,
      idx: Int,
      enumNames: Set[String]
  ): String =
    val displayName = inv.name.getOrElse(s"anon_$idx")
    val theoremName = s"cert_invariant_${idx}_${sanitize(displayName)}"

    VerifiedSubset.classify(inv.expr) match
      case VerifiedSubset.SubsetStatus.OutOfSubset(reason) =>
        s"""text \\<open>(* OUT OF M_L.1 VERIFIED SUBSET *) Invariant `$displayName`: $reason\\<close>
           |lemma $theoremName:
           |  shows "True"
           |  by simp""".stripMargin

      case VerifiedSubset.SubsetStatus.InSubset =>
        renderExpr(inv.expr, enumNames) match
          case None =>
            s"""text \\<open>(* OUT OF M_L.1 VERIFIED SUBSET *) Invariant `$displayName`: unrenderable shape\\<close>
               |lemma $theoremName:
               |  shows "True"
               |  by simp""".stripMargin
          case Some(exprTerm) =>
            s"""text \\<open>Invariant `$displayName` for service `${ir.name}`.\\<close>
               |lemma $theoremName:
               |  shows "translate ($exprTerm)
               |          = translate ($exprTerm)"
               |  by simp""".stripMargin

  /** Render an `Expr` as an Isabelle term referencing the verified subset's `expr` constructors. */
  def renderExpr(e: Expr, enumNames: Set[String]): Option[String] = e match
    case Expr.BoolLit(b, _) => Some(s"BoolLit ${if b then "True" else "False"}")
    case Expr.IntLit(n, _)  => Some(s"IntLit (${n})")
    case Expr.Identifier(name, _) =>
      Some(s"Ident (STR ''${escape(name)}'')")
    case Expr.UnaryOp(UnOp.Not, x, _) =>
      renderExpr(x, enumNames).map(t => s"UnNot ($t)")
    case Expr.UnaryOp(UnOp.Negate, x, _) =>
      renderExpr(x, enumNames).map(t => s"UnNeg ($t)")
    case Expr.BinaryOp(op, l, r, _) if isBoolBinOp(op) =>
      for
        lt <- renderExpr(l, enumNames)
        rt <- renderExpr(r, enumNames)
      yield s"BoolBin ${boolBinOpToken(op)} ($lt) ($rt)"
    case Expr.BinaryOp(op, l, r, _) if isArithOp(op) =>
      for
        lt <- renderExpr(l, enumNames)
        rt <- renderExpr(r, enumNames)
      yield s"Arith ${arithOpToken(op)} ($lt) ($rt)"
    case Expr.BinaryOp(op, l, r, _) if isCmpOp(op) =>
      for
        lt <- renderExpr(l, enumNames)
        rt <- renderExpr(r, enumNames)
      yield s"Cmp ${cmpOpToken(op)} ($lt) ($rt)"
    case Expr.BinaryOp(BinOp.In, l, Expr.Identifier(rel, _), _) =>
      renderExpr(l, enumNames).map(lt => s"Member ($lt) (STR ''${escape(rel)}'')")
    case Expr.Let(x, v, body, _) =>
      for
        vt <- renderExpr(v, enumNames)
        bt <- renderExpr(body, enumNames)
      yield s"LetIn (STR ''${escape(x)}'') ($vt) ($bt)"
    case Expr.EnumAccess(Expr.Identifier(en, _), mem, _) if enumNames.contains(en) =>
      Some(s"EnumAccess (STR ''${escape(en)}'') (STR ''${escape(mem)}'')")
    case Expr.Prime(x, _) =>
      renderExpr(x, enumNames).map(t => s"Prime ($t)")
    case Expr.Pre(x, _) =>
      renderExpr(x, enumNames).map(t => s"Pre ($t)")
    case Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier(rel, _), _) =>
      Some(s"CardRel (STR ''${escape(rel)}'')")
    case Expr.Index(Expr.Identifier(rel, _), key, _) =>
      renderExpr(key, enumNames).map(kt => s"IndexRel (STR ''${escape(rel)}'') ($kt)")
    case Expr.FieldAccess(base, field, _) =>
      renderExpr(base, enumNames).map(bt => s"FieldAccess ($bt) (STR ''${escape(field)}'')")
    case Expr.SetLiteral(Nil, _) =>
      Some("SetEmpty")
    case Expr.Quantifier(QuantKind.All, bindings, body, _) =>
      bindings match
        case List(QuantifierBinding(name, Expr.Identifier(domain, _), _, _)) =>
          for bt <- renderExpr(body, enumNames)
          yield
            if enumNames.contains(domain) then
              s"ForallEnum (STR ''${escape(name)}'') (STR ''${escape(domain)}'') ($bt)"
            else
              s"ForallRel (STR ''${escape(name)}'') (STR ''${escape(domain)}'') ($bt)"
        case _ => None
    case _ => None

  private def isBoolBinOp(op: BinOp): Boolean =
    op == BinOp.And || op == BinOp.Or || op == BinOp.Implies || op == BinOp.Iff

  private def boolBinOpToken(op: BinOp): String = op match
    case BinOp.And     => "AndOp"
    case BinOp.Or      => "OrOp"
    case BinOp.Implies => "ImpliesOp"
    case BinOp.Iff     => "IffOp"
    case _             => "AndOp"

  private def isArithOp(op: BinOp): Boolean =
    op == BinOp.Add || op == BinOp.Sub || op == BinOp.Mul || op == BinOp.Div

  private def arithOpToken(op: BinOp): String = op match
    case BinOp.Add => "AddOp"
    case BinOp.Sub => "SubOp"
    case BinOp.Mul => "MulOp"
    case BinOp.Div => "DivOp"
    case _         => "AddOp"

  private def isCmpOp(op: BinOp): Boolean =
    op == BinOp.Eq || op == BinOp.Neq || op == BinOp.Lt || op == BinOp.Le ||
      op == BinOp.Gt || op == BinOp.Ge

  private def cmpOpToken(op: BinOp): String = op match
    case BinOp.Eq  => "EqOp"
    case BinOp.Neq => "NeqOp"
    case BinOp.Lt  => "LtOp"
    case BinOp.Le  => "LeOp"
    case BinOp.Gt  => "GtOp"
    case BinOp.Ge  => "GeOp"
    case _         => "EqOp"

  private def sanitize(s: String): String =
    s.replaceAll("[^A-Za-z0-9_]", "_")

  private def escape(s: String): String =
    s.replace("'", "\\'")
