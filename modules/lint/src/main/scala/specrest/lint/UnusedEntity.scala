package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

object UnusedEntity extends LintPass:
  val code = "L05"

  def run(ir: service_ir_full): List[LintDiagnostic] =
    val refs = referencedNames(ir)
    ir.c.flatMap: e =>
      if refs.contains(e.name) then Nil
      else
        List(
          LintDiagnostic(
            code,
            LintLevel.Warning,
            s"entity '${e.name}' is declared but never referenced in state, operations, invariants, or other entities",
            e.span
          )
        )

  private def referencedNames(ir: service_ir_full): Set[String] =
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

    ir.state.toList.flatMap(_.fields).foreach(f => collectType(f.typeExpr))

    for op <- ir.g do
      op.b.foreach(p => collectType(p.typeExpr))
      op.c.foreach(p => collectType(p.typeExpr))
      op.d.foreach(collectExpr)
      op.e.foreach(collectExpr)

    for ent <- ir.c do
      ent.extends_.foreach(p => acc += p)
      ent.fields.foreach: f =>
        collectType(f.typeExpr)
        f.c.foreach(collectExpr)
      ent.invariants.foreach(collectExpr)

    ir.invariants.foreach(i => collectExpr(i.expr))
    ir.j.foreach(t => collectExpr(t.expr))
    ir.k.foreach(f => collectExpr(f.expr))

    for fn <- ir.l do
      fn.params.foreach(p => collectType(p.typeExpr))
      collectType(fn.c)
      collectExpr(fn.body)

    for pr <- ir.m do
      pr.params.foreach(p => collectType(p.typeExpr))
      collectExpr(pr.body)

    for tr <- ir.h do
      tr.rules.foreach(_.d.foreach(collectExpr))

    ir.e.foreach: a =>
      collectType(a.typeExpr)
      a.c.foreach(collectExpr)

    acc.toSet
