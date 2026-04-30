package specrest.lint

import specrest.ir.BinOp
import specrest.ir.Expr
import specrest.ir.ServiceIR
import specrest.ir.UnOp

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object TypeMismatch extends LintPass:
  val code = "L01"

  private enum LitClass derives CanEqual:
    case Numeric, Bool, StringLike, Collection, NoneLit

  private def litClass(e: Expr): Option[LitClass] = e match
    case _: Expr.IntLit    => Some(LitClass.Numeric)
    case _: Expr.FloatLit  => Some(LitClass.Numeric)
    case _: Expr.BoolLit   => Some(LitClass.Bool)
    case _: Expr.StringLit => Some(LitClass.StringLike)
    case _: Expr.SetLiteral | _: Expr.MapLiteral | _: Expr.SeqLiteral =>
      Some(LitClass.Collection)
    case _: Expr.NoneLit => Some(LitClass.NoneLit)
    case _               => None

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val out = List.newBuilder[LintDiagnostic]
    val visit: Expr => Unit =
      case e @ Expr.BinaryOp(op, left, right, span) =>
        checkBinary(op, left, right, span.orElse(e.spanOpt), out)
      case e @ Expr.UnaryOp(UnOp.Not, operand, span) =>
        litClass(operand) match
          case Some(c) if c != LitClass.Bool =>
            out += LintDiagnostic(
              code,
              LintLevel.Error,
              s"logical 'not' applied to a ${describe(c)} literal",
              span.orElse(e.spanOpt)
            )
          case _ => ()
      case e @ Expr.UnaryOp(UnOp.Negate, operand, span) =>
        litClass(operand) match
          case Some(c) if c != LitClass.Numeric =>
            out += LintDiagnostic(
              code,
              LintLevel.Error,
              s"arithmetic '-' applied to a ${describe(c)} literal",
              span.orElse(e.spanOpt)
            )
          case _ => ()
      case _ => ()

    def visitAll(e: Expr): Unit = ExprWalk.foreach(e)(visit)

    for op <- ir.operations do
      op.requires.foreach(visitAll)
      op.ensures.foreach(visitAll)
    ir.invariants.foreach(i => visitAll(i.expr))
    ir.temporals.foreach(t => visitAll(t.expr))
    ir.facts.foreach(f => visitAll(f.expr))
    ir.functions.foreach(f => visitAll(f.body))
    ir.predicates.foreach(p => visitAll(p.body))
    ir.entities.foreach: ent =>
      ent.fields.foreach(_.constraint.foreach(visitAll))
      ent.invariants.foreach(visitAll)
    ir.typeAliases.foreach(_.constraint.foreach(visitAll))

    out.result()

  private def checkBinary(
      op: BinOp,
      left: Expr,
      right: Expr,
      span: Option[specrest.ir.Span],
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]]
  ): Unit =
    val lc = litClass(left)
    val rc = litClass(right)
    op match
      case BinOp.Add | BinOp.Sub | BinOp.Mul | BinOp.Div =>
        // `+` and `-` are overloaded for set/map union and diff in this DSL,
        // and `+` for string concatenation. Only flag when a literal is clearly
        // never a number (Bool or None) — those cases admit no overload.
        val bad = lc.exists(c => c == LitClass.Bool || c == LitClass.NoneLit) ||
          rc.exists(c => c == LitClass.Bool || c == LitClass.NoneLit)
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"arithmetic '${binOpName(op)}' has a ${describe((lc ++ rc).find(c => c == LitClass.Bool || c == LitClass.NoneLit).get)} literal operand",
            span
          )
      case BinOp.Lt | BinOp.Gt | BinOp.Le | BinOp.Ge =>
        // Comparisons can apply to numbers, strings (lexicographic), or sets
        // (subset/superset). Only flag on Bool/None literals (never ordered).
        val bad = lc.exists(c => c == LitClass.Bool || c == LitClass.NoneLit) ||
          rc.exists(c => c == LitClass.Bool || c == LitClass.NoneLit)
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"comparison '${binOpName(op)}' has a ${describe((lc ++ rc).find(c => c == LitClass.Bool || c == LitClass.NoneLit).get)} literal operand",
            span
          )
      case BinOp.And | BinOp.Or | BinOp.Implies | BinOp.Iff =>
        val bad = lc.exists(_ != LitClass.Bool) || rc.exists(_ != LitClass.Bool)
        if bad then
          val offender = (lc ++ rc).find(_ != LitClass.Bool).get
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"logical '${binOpName(op)}' applied to a ${describe(offender)} literal",
            span
          )
      case BinOp.In | BinOp.NotIn =>
        val bad = rc.exists(c =>
          c == LitClass.Numeric || c == LitClass.Bool || c == LitClass.StringLike || c == LitClass.NoneLit
        )
        if bad then
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"'${binOpName(op)}' right operand is a ${describe(rc.get)} literal, not a collection",
            span
          )
      case _ => ()

  private def describe(c: LitClass): String = c match
    case LitClass.Numeric    => "numeric"
    case LitClass.Bool       => "boolean"
    case LitClass.StringLike => "string"
    case LitClass.Collection => "collection"
    case LitClass.NoneLit    => "none"

  private def binOpName(op: BinOp): String = op match
    case BinOp.Add     => "+"
    case BinOp.Sub     => "-"
    case BinOp.Mul     => "*"
    case BinOp.Div     => "/"
    case BinOp.Lt      => "<"
    case BinOp.Gt      => ">"
    case BinOp.Le      => "<="
    case BinOp.Ge      => ">="
    case BinOp.And     => "and"
    case BinOp.Or      => "or"
    case BinOp.Implies => "implies"
    case BinOp.Iff     => "iff"
    case BinOp.In      => "in"
    case BinOp.NotIn   => "not in"
    case BinOp.Eq      => "="
    case BinOp.Neq     => "!="
    case other         => other.toString
