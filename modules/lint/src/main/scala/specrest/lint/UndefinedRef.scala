package specrest.lint

import specrest.convention.Builtins
import specrest.ir.generated.SpecRestGenerated.*

object UndefinedRef extends LintPass:
  val code = "L02"

  def run(ir: service_ir_full): List[LintDiagnostic] =
    val out         = List.newBuilder[LintDiagnostic]
    val stateFields = irStateFieldNames(ir).toSet
    val entityNames = svcEntities(ir).map(entName).toSet
    val enumNames   = svcEnums(ir).map(enmName).toSet
    val enumMembers = svcEnums(ir).flatMap(enmVariants).toSet
    val typeAliases = svcTypeAliases(ir).map(talName).toSet
    val predicates  = svcPredicates(ir).map(prdName).toSet
    val functions   = svcFunctions(ir).map(fncName).toSet
    val factImplicit = svcFacts(ir).flatMap { f =>
      // Fact bodies sometimes call helper predicates by name with no
      // explicit declaration; treat any Call(Identifier, _) callee as
      // defined to avoid false positives. (Crucially, we whitelist
      // CALLEE names only — not arbitrary identifiers from the fact
      // body, which would silently mask real undefined-ref errors.)
      collectAllCallNames(fctBody(f))
    }.toSet
    val global =
      stateFields ++ entityNames ++ enumNames ++ enumMembers ++ typeAliases ++
        predicates ++ functions ++ Builtins.names ++ factImplicit

    def emit(refs: List[(String, Option[span_t])]): Unit =
      refs.foreach { case (name, span) =>
        out += LintDiagnostic(
          code,
          LintLevel.Error,
          s"undefined identifier '$name'",
          span
        )
      }

    def check(expr: expr_full, scope: Set[String]): Unit =
      emit(walkUndefinedExpr(expr, scope.toList))

    for op <- svcOperations(ir) do
      val opScope = global ++ operInputs(op).map(prmName) ++
        operOutputs(op).map(prmName) + "self"
      operRequires(op).foreach(check(_, opScope))
      operEnsures(op).foreach(check(_, opScope))

    svcInvariants(ir).foreach(inv => check(invBody(inv), global + "self"))
    svcTemporals(ir).foreach(t => check(temporalArg(tmpBody(t)), global + "self"))
    svcFacts(ir).foreach(f => check(fctBody(f), global + "self"))

    for e <- svcEntities(ir) do
      val entScope = global + "self" + "value" ++ entFields(e).map(fldName)
      entFields(e).foreach(f => fldDefault(f).foreach(check(_, entScope)))
      entInvariants(e).foreach(check(_, entScope))

    for a <- svcTypeAliases(ir) do talConstraint(a).foreach(check(_, global + "value"))

    for fn <- svcFunctions(ir) do
      check(fncBody(fn), global ++ fncParams(fn).map(prmName))

    for p <- svcPredicates(ir) do
      check(prdBody(p), global ++ prdParams(p).map(prmName))

    for tr <- svcTransitions(ir) do
      val entFieldNames = svcEntities(ir)
        .filter(e => entName(e) == trnEntity(tr))
        .map(e => entFields(e).map(fldName).toSet)
        .headOption
        .getOrElse(Set.empty)
      val trScope = global ++ entFieldNames + "self"
      trnRules(tr).foreach(r => trlGuard(r).foreach(check(_, trScope)))

    out.result()
