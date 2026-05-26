package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object UnusedEntity extends LintPass:
  val code = "L05"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val refs = referencedNames(ir)
    ir.c.flatMap { case EntityDeclFull(name, _, _, _, span) =>
      if refs.contains(name) then Nil
      else
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"entity '$name' is declared but never referenced in state, operations, invariants, or other entities",
            span
          )
        )
    }

  // Single mutable Set accumulator: O(1) amortized membership and
  // bounded by the number of distinct names. Matches the original
  // walker shape — avoids materialising duplicate-heavy intermediate
  // Lists per IR slot before deduplication.
  private def referencedNames(ir: ServiceIRFull): Set[String] =
    val acc = scala.collection.mutable.Set.empty[String]

    def addExpr(e: expr_full): Unit      = acc ++= collectExprNames(e)
    def addType(t: type_expr_full): Unit = acc ++= collectTypeNames(t)

    ir.f.toList.foreach { case StateDeclFull(fs, _) =>
      fs.foreach { case StateFieldDeclFull(_, t, _) => addType(t) }
    }

    for case OperationDeclFull(_, inputs, outputs, requires, ensures, _) <- ir.g do
      inputs.foreach { case ParamDeclFull(_, t, _) => addType(t) }
      outputs.foreach { case ParamDeclFull(_, t, _) => addType(t) }
      requires.foreach(addExpr)
      ensures.foreach(addExpr)

    for case EntityDeclFull(_, parent, fields, invs, _) <- ir.c do
      parent.foreach(acc += _)
      fields.foreach { case FieldDeclFull(_, t, c, _) =>
        addType(t); c.foreach(addExpr)
      }
      invs.foreach(addExpr)

    ir.i.foreach { case InvariantDeclFull(_, e, _) => addExpr(e) }
    ir.j.foreach { case TemporalDeclFull(_, b, _) => addExpr(temporalArg(b)) }
    ir.k.foreach { case FactDeclFull(_, e, _) => addExpr(e) }

    for case FunctionDeclFull(_, params, ret, body, _) <- ir.l do
      params.foreach { case ParamDeclFull(_, t, _) => addType(t) }
      addType(ret); addExpr(body)

    for case PredicateDeclFull(_, params, body, _) <- ir.m do
      params.foreach { case ParamDeclFull(_, t, _) => addType(t) }
      addExpr(body)

    for case TransitionDeclFull(_, _, _, rules, _) <- ir.h do
      rules.foreach { case TransitionRuleFull(_, _, _, guard, _) =>
        guard.foreach(addExpr)
      }

    ir.e.foreach { case TypeAliasDeclFull(_, t, c, _) =>
      addType(t); c.foreach(addExpr)
    }

    acc.toSet
