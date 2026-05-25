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

  private def referencedNames(ir: ServiceIRFull): Set[String] =
    val acc = scala.collection.mutable.Set.empty[String]

    def collectType(t: type_expr_full): Unit = t match
      case NamedTypeF(n, _)          => acc += n
      case SetTypeF(inner, _)        => collectType(inner)
      case SeqTypeF(inner, _)        => collectType(inner)
      case OptionTypeF(inner, _)     => collectType(inner)
      case MapTypeF(k, v, _)         => collectType(k); collectType(v)
      case RelationTypeF(f, _, t, _) => collectType(f); collectType(t)

    def collectExpr(e: expr_full): Unit =
      ExprWalk.foreach(e):
        case ConstructorF(name, _, _) => acc += name
        case IdentifierF(name, _)     => acc += name
        case EnumAccessF(_, _, _)     => () // handled via Identifier on base
        case _                        => ()

    ir.f.toList.flatMap { case StateDeclFull(fs, _) => fs }.foreach {
      case StateFieldDeclFull(_, t, _) => collectType(t)
    }

    for case OperationDeclFull(_, inputs, outputs, requires, ensures, _) <- ir.g do
      inputs.foreach { case ParamDeclFull(_, t, _) => collectType(t) }
      outputs.foreach { case ParamDeclFull(_, t, _) => collectType(t) }
      requires.foreach(collectExpr)
      ensures.foreach(collectExpr)

    for case EntityDeclFull(_, parent, fields, invs, _) <- ir.c do
      parent.foreach(p => acc += p)
      fields.foreach { case FieldDeclFull(_, t, c, _) =>
        collectType(t)
        c.foreach(collectExpr)
      }
      invs.foreach(collectExpr)

    ir.i.foreach { case InvariantDeclFull(_, e, _) => collectExpr(e) }
    ir.j.foreach { case TemporalDeclFull(_, b, _) => collectExpr(temporalArg(b)) }
    ir.k.foreach { case FactDeclFull(_, e, _) => collectExpr(e) }

    for case FunctionDeclFull(_, params, ret, body, _) <- ir.l do
      params.foreach { case ParamDeclFull(_, t, _) => collectType(t) }
      collectType(ret)
      collectExpr(body)

    for case PredicateDeclFull(_, params, body, _) <- ir.m do
      params.foreach { case ParamDeclFull(_, t, _) => collectType(t) }
      collectExpr(body)

    for case TransitionDeclFull(_, _, _, rules, _) <- ir.h do
      rules.foreach { case TransitionRuleFull(_, _, _, guard, _) => guard.foreach(collectExpr) }

    ir.e.foreach { case TypeAliasDeclFull(_, t, c, _) =>
      collectType(t)
      c.foreach(collectExpr)
    }

    acc.toSet
