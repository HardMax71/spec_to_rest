package specrest.lint

import specrest.ir.Expr
import specrest.ir.ServiceIR
import specrest.ir.TypeExpr

object UnusedEntity extends LintPass:
  val code = "L05"

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val refs    = referencedNames(ir)
    val builtin = Set("Int", "Long", "Float", "Double", "Bool", "Boolean", "String", "DateTime")
    ir.entities.flatMap: e =>
      if refs.contains(e.name) || builtin.contains(e.name) then Nil
      else
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"entity '${e.name}' is declared but never referenced in state, operations, invariants, or other entities",
            e.span
          )
        )

  private def referencedNames(ir: ServiceIR): Set[String] =
    val acc = scala.collection.mutable.Set.empty[String]

    def collectType(t: TypeExpr): Unit = t match
      case TypeExpr.NamedType(n, _)          => acc += n
      case TypeExpr.SetType(inner, _)        => collectType(inner)
      case TypeExpr.SeqType(inner, _)        => collectType(inner)
      case TypeExpr.OptionType(inner, _)     => collectType(inner)
      case TypeExpr.MapType(k, v, _)         => collectType(k); collectType(v)
      case TypeExpr.RelationType(f, _, t, _) => collectType(f); collectType(t)

    def collectExpr(e: Expr): Unit =
      ExprWalk.foreach(e):
        case Expr.Constructor(name, _, _) => acc += name
        case Expr.Identifier(name, _)     => acc += name
        case Expr.EnumAccess(_, _, _)     => () // handled via Identifier on base
        case _                            => ()

    ir.state.toList.flatMap(_.fields).foreach(f => collectType(f.typeExpr))

    for op <- ir.operations do
      op.inputs.foreach(p => collectType(p.typeExpr))
      op.outputs.foreach(p => collectType(p.typeExpr))
      op.requires.foreach(collectExpr)
      op.ensures.foreach(collectExpr)

    for ent <- ir.entities do
      ent.fields.foreach: f =>
        collectType(f.typeExpr)
        f.constraint.foreach(collectExpr)
      ent.invariants.foreach(collectExpr)

    ir.invariants.foreach(i => collectExpr(i.expr))
    ir.temporals.foreach(t => collectExpr(t.expr))
    ir.facts.foreach(f => collectExpr(f.expr))

    for fn <- ir.functions do
      fn.params.foreach(p => collectType(p.typeExpr))
      collectType(fn.returnType)
      collectExpr(fn.body)

    for pr <- ir.predicates do
      pr.params.foreach(p => collectType(p.typeExpr))
      collectExpr(pr.body)

    for tr <- ir.transitions do
      tr.rules.foreach(_.guard.foreach(collectExpr))

    ir.typeAliases.foreach: a =>
      collectType(a.typeExpr)
      a.constraint.foreach(collectExpr)

    acc.toSet
