package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
object TypeMismatch extends LintPass:
  val code = "L01"

  private enum LitClass derives CanEqual:
    case Numeric, Bool, StringLike, Collection, NoneLit

  private def litClass(e: expr_full): Option[LitClass] = e match
    case _: IntLitF    => Some(LitClass.Numeric)
    case _: FloatLitF  => Some(LitClass.Numeric)
    case _: BoolLitF   => Some(LitClass.Bool)
    case _: StringLitF => Some(LitClass.StringLike)
    case _: SetLiteralF | _: MapLiteralF | _: SeqLiteralF =>
      Some(LitClass.Collection)
    case _: NoneLitF => Some(LitClass.NoneLit)
    case _           => None

  def run(ir: service_ir_full): List[LintDiagnostic] =
    val out = List.newBuilder[LintDiagnostic]
    val visit: expr_full => Unit =
      case e @ BinaryOpF(op, left, right, span) =>
        checkBinary(op, left, right, span.orElse(e.spanOpt), out)
      case e @ UnaryOpF(UNot(), operand, span) =>
        litClass(operand) match
          case Some(c) if c != LitClass.Bool =>
            out += LintDiagnostic(
              code,
              LintLevel.Error,
              s"logical 'not' applied to a ${describe(c)} literal",
              span.orElse(e.spanOpt)
            )
          case _ => ()
      case e @ UnaryOpF(UNegate(), operand, span) =>
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

    def visitAll(e: expr_full): Unit = ExprWalk.foreach(e)(visit)

    for op <- ir.g do
      op.d.foreach(visitAll)
      op.e.foreach(visitAll)
    ir.invariants.foreach(i => visitAll(i.expr))
    ir.j.foreach(t => visitAll(t.expr))
    ir.k.foreach(f => visitAll(f.expr))
    ir.l.foreach(f => visitAll(f.body))
    ir.m.foreach(p => visitAll(p.body))
    ir.c.foreach: ent =>
      ent.fields.foreach(_.c.foreach(visitAll))
      ent.invariants.foreach(visitAll)
    ir.e.foreach(_.c.foreach(visitAll))

    out.result()

  private def checkBinary(
      op: bin_op_full,
      left: expr_full,
      right: expr_full,
      span: Option[specrest.ir.span_t],
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]]
  ): Unit =
    val lc = litClass(left)
    val rc = litClass(right)
    op match
      case BAdd() | BSub() | BMul() | BDiv() =>
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
      case BLt() | BGt() | BLe() | BGe() =>
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
      case BAnd() | BOr() | BImplies() | BIff() =>
        val bad = lc.exists(_ != LitClass.Bool) || rc.exists(_ != LitClass.Bool)
        if bad then
          val offender = (lc ++ rc).find(_ != LitClass.Bool).get
          out += LintDiagnostic(
            code,
            LintLevel.Error,
            s"logical '${binOpName(op)}' applied to a ${describe(offender)} literal",
            span
          )
      case BIn() | BNotIn() =>
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

  private def binOpName(op: bin_op_full): String = op match
    case BAdd()     => "+"
    case BSub()     => "-"
    case BMul()     => "*"
    case BDiv()     => "/"
    case BLt()      => "<"
    case BGt()      => ">"
    case BLe()      => "<="
    case BGe()      => ">="
    case BAnd()     => "and"
    case BOr()      => "or"
    case BImplies() => "implies"
    case BIff()     => "iff"
    case BIn()      => "in"
    case BNotIn()   => "not in"
    case BEq()      => "="
    case BNeq()     => "!="
    case other      => other.toString
