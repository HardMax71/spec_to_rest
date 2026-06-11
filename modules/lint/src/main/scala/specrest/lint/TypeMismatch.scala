package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object TypeMismatch extends LintPass:
  val code = "L01"

  def run(ir: service_ir): List[LintDiagnostic] =
    val out = List.newBuilder[LintDiagnostic]

    def visitAll(e: expr): Unit =
      typeMismatchDiagnostics(e).foreach { case (kind, span) =>
        out += LintDiagnostic(code, LintLevel.Error, render(kind), span)
      }

    for op <- svcOperations(ir) do
      operRequires(op).foreach(visitAll)
      operEnsures(op).foreach(visitAll)
    svcInvariants(ir).foreach(inv => visitAll(invBody(inv)))
    svcTemporals(ir).foreach(t => visitAll(temporalArg(tmpBody(t))))
    svcFacts(ir).foreach(f => visitAll(fctBody(f)))
    svcFunctions(ir).foreach(fn => visitAll(fncBody(fn)))
    svcPredicates(ir).foreach(p => visitAll(prdBody(p)))
    svcEntities(ir).foreach { e =>
      entFields(e).foreach(f => fldDefault(f).foreach(visitAll))
      entInvariants(e).foreach(visitAll)
    }
    svcTypeAliases(ir).foreach(a => talConstraint(a).foreach(visitAll))

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
