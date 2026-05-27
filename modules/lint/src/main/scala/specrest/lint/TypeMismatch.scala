package specrest.lint

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

object TypeMismatch extends LintPass:
  val code = "L01"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val out = List.newBuilder[LintDiagnostic]

    def visitAll(e: expr_full): Unit =
      SpecRestGenerated.typeMismatchDiagnostics(e).foreach { case (kind, span) =>
        out += LintDiagnostic(code, LintLevel.Error, render(kind), span)
      }

    for case OperationDeclFull(_, _, _, requires, ensures, _) <- ir.g do
      requires.foreach(visitAll)
      ensures.foreach(visitAll)
    ir.i.foreach { case InvariantDeclFull(_, e, _) => visitAll(e) }
    ir.j.foreach { case TemporalDeclFull(_, b, _) => visitAll(temporalArg(b)) }
    ir.k.foreach { case FactDeclFull(_, e, _) => visitAll(e) }
    ir.l.foreach { case FunctionDeclFull(_, _, _, body, _) => visitAll(body) }
    ir.m.foreach { case PredicateDeclFull(_, _, body, _) => visitAll(body) }
    ir.c.foreach { case EntityDeclFull(_, _, fields, invs, _) =>
      fields.foreach { case FieldDeclFull(_, _, c, _) => c.foreach(visitAll) }
      invs.foreach(visitAll)
    }
    ir.e.foreach { case TypeAliasDeclFull(_, _, c, _) => c.foreach(visitAll) }

    out.result()

  private def render(kind: type_mismatch_kind): String = kind match
    case TmUnaryNotOnNonBool(c) =>
      s"logical 'not' applied to a ${describeLitClass(c)} literal"
    case TmUnaryNegOnNonNumeric(c) =>
      s"arithmetic '-' applied to a ${describeLitClass(c)} literal"
    case TmArithLitMisuse(op, c) =>
      s"arithmetic '${binOpName(op)}' has a ${describeLitClass(c)} literal operand"
    case TmCompareLitMisuse(op, c) =>
      s"comparison '${binOpName(op)}' has a ${describeLitClass(c)} literal operand"
    case TmLogicalLitMisuse(op, c) =>
      s"logical '${binOpName(op)}' applied to a ${describeLitClass(c)} literal"
    case TmMembershipLitMisuse(op, c) =>
      s"'${binOpName(op)}' right operand is a ${describeLitClass(c)} literal, not a collection"
