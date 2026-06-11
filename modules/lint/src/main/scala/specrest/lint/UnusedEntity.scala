package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object UnusedEntity extends LintPass:
  val code = "L05"

  def run(ir: service_ir): List[LintDiagnostic] =
    val refs = referencedNames(ir)
    svcEntities(ir).flatMap { e =>
      val name = entName(e)
      if refs.contains(name) then Nil
      else
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"entity '$name' is declared but never referenced in state, operations, invariants, or other entities",
            entSpan(e)
          )
        )
    }

  // Single mutable Set accumulator: O(1) amortized membership and
  // bounded by the number of distinct names. Matches the original
  // walker shape — avoids materialising duplicate-heavy intermediate
  // Lists per IR slot before deduplication.
  private def referencedNames(ir: service_ir): Set[String] =
    val acc = scala.collection.mutable.Set.empty[String]

    def addExpr(e: expr): Unit      = acc ++= collectExprNames(e)
    def addType(t: type_expr): Unit = acc ++= collectTypeNames(t)

    irStateFields(ir).foreach(sf => addType(stfType(sf)))

    for op <- svcOperations(ir) do
      operInputs(op).foreach(p => addType(prmType(p)))
      operOutputs(op).foreach(p => addType(prmType(p)))
      operRequires(op).foreach(addExpr)
      operEnsures(op).foreach(addExpr)

    for e <- svcEntities(ir) do
      entParent(e).foreach(acc += _)
      entFields(e).foreach { f =>
        addType(fldType(f)); fldDefault(f).foreach(addExpr)
      }
      entInvariants(e).foreach(addExpr)

    svcInvariants(ir).foreach(inv => addExpr(invBody(inv)))
    svcTemporals(ir).foreach(t => addExpr(temporalArg(tmpBody(t))))
    svcFacts(ir).foreach(f => addExpr(fctBody(f)))

    for fn <- svcFunctions(ir) do
      fncParams(fn).foreach(p => addType(prmType(p)))
      addType(fncRetType(fn)); addExpr(fncBody(fn))

    for p <- svcPredicates(ir) do
      prdParams(p).foreach(pp => addType(prmType(pp)))
      addExpr(prdBody(p))

    for tr <- svcTransitions(ir) do
      trnRules(tr).foreach(r => trlGuard(r).foreach(addExpr))

    svcTypeAliases(ir).foreach { a =>
      addType(talType(a)); talConstraint(a).foreach(addExpr)
    }

    acc.toSet
