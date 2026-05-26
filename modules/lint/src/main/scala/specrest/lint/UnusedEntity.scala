package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object UnusedEntity extends LintPass:
  val code = "L05"

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val refs = referencedNames(ir).toSet
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

  private def referencedNames(ir: ServiceIRFull): List[String] =
    val state = ir.f.toList.flatMap { case StateDeclFull(fs, _) =>
      fs.flatMap { case StateFieldDeclFull(_, t, _) => collectTypeNames(t) }
    }

    val ops = ir.g.collect { case op: OperationDeclFull => op }
      .flatMap: op =>
        op.b.flatMap { case ParamDeclFull(_, t, _) => collectTypeNames(t) } ++
          op.c.flatMap { case ParamDeclFull(_, t, _) => collectTypeNames(t) } ++
          op.d.flatMap(collectExprNames) ++
          op.e.flatMap(collectExprNames)

    val entities = ir.c.collect { case e: EntityDeclFull => e }
      .flatMap: e =>
        e.b.toList ++
          e.c.flatMap { case FieldDeclFull(_, t, c, _) =>
            collectTypeNames(t) ++ c.toList.flatMap(collectExprNames)
          } ++
          e.d.flatMap(collectExprNames)

    val invs  = ir.i.flatMap { case InvariantDeclFull(_, e, _) => collectExprNames(e) }
    val temps = ir.j.flatMap { case TemporalDeclFull(_, b, _) => collectExprNames(temporalArg(b)) }
    val facts = ir.k.flatMap { case FactDeclFull(_, e, _) => collectExprNames(e) }

    val funcs = ir.l.collect { case f: FunctionDeclFull => f }.flatMap: f =>
      f.b.flatMap { case ParamDeclFull(_, t, _) => collectTypeNames(t) } ++
        collectTypeNames(f.c) ++ collectExprNames(f.d)

    val preds = ir.m.collect { case p: PredicateDeclFull => p }.flatMap: p =>
      p.b.flatMap { case ParamDeclFull(_, t, _) => collectTypeNames(t) } ++
        collectExprNames(p.c)

    val transitions = ir.h.collect { case t: TransitionDeclFull => t }.flatMap: td =>
      td.d.flatMap { case TransitionRuleFull(_, _, _, guard, _) =>
        guard.toList.flatMap(collectExprNames)
      }

    val aliases = ir.e.collect { case a: TypeAliasDeclFull => a }.flatMap: a =>
      collectTypeNames(a.b) ++ a.c.toList.flatMap(collectExprNames)

    state ++ ops ++ entities ++ invs ++ temps ++ facts ++ funcs ++ preds ++
      transitions ++ aliases
