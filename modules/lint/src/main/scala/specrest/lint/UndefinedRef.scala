package specrest.lint

import specrest.ir.Expr
import specrest.ir.ServiceIR

object UndefinedRef extends LintPass:
  val code = "L02"

  private val builtins: Set[String] = Set(
    "len",
    "dom",
    "ran",
    "abs",
    "hash",
    "now",
    "sum",
    "minutes",
    "hours",
    "days",
    "seconds"
  )

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val out          = List.newBuilder[LintDiagnostic]
    val stateFields  = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val entityNames  = ir.entities.map(_.name).toSet
    val enumNames    = ir.enums.map(_.name).toSet
    val enumMembers  = ir.enums.flatMap(_.values).toSet
    val typeAliases  = ir.typeAliases.map(_.name).toSet
    val predicates   = ir.predicates.map(_.name).toSet
    val functions    = ir.functions.map(_.name).toSet
    val factImplicit = ir.facts.flatMap(f => collectCallees(f.expr)).toSet
    val global =
      stateFields ++ entityNames ++ enumNames ++ enumMembers ++ typeAliases ++
        predicates ++ functions ++ builtins ++ factImplicit

    def check(expr: Expr, scope: Set[String]): Unit =
      walk(expr, scope, out)

    for op <- ir.operations do
      val opScope = global ++ op.inputs.map(_.name) ++ op.outputs.map(_.name) + "self"
      op.requires.foreach(check(_, opScope))
      op.ensures.foreach(check(_, opScope))

    ir.invariants.foreach(i => check(i.expr, global + "self"))
    ir.temporals.foreach(t => check(t.expr, global + "self"))
    ir.facts.foreach(f => check(f.expr, global + "self"))

    for ent <- ir.entities do
      val entScope = global + "self" + "value" ++ ent.fields.map(_.name)
      ent.fields.foreach(_.constraint.foreach(check(_, entScope)))
      ent.invariants.foreach(check(_, entScope))

    for a <- ir.typeAliases do a.constraint.foreach(check(_, global + "value"))

    for fn <- ir.functions do check(fn.body, global ++ fn.params.map(_.name))

    for pr <- ir.predicates do check(pr.body, global ++ pr.params.map(_.name))

    for tr <- ir.transitions do
      val entFields = ir.entities
        .find(_.name == tr.entityName)
        .map(_.fields.map(_.name).toSet)
        .getOrElse(Set.empty)
      val trScope = global ++ entFields + "self"
      tr.rules.foreach(_.guard.foreach(check(_, trScope)))

    out.result()

  private def collectCallees(e: Expr): List[String] =
    val acc = scala.collection.mutable.ListBuffer.empty[String]
    ExprWalk.foreach(e):
      case Expr.Call(Expr.Identifier(n, _), _, _) => acc += n
      case _                                      => ()
    acc.toList

  private def walk(
      expr: Expr,
      scope: Set[String],
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]]
  ): Unit = expr match
    case Expr.Identifier(name, span) =>
      if !scope.contains(name) then
        out += LintDiagnostic(
          UndefinedRef.code,
          LintLevel.Error,
          s"undefined identifier '$name'",
          span
        )
    case Expr.BinaryOp(_, l, r, _) =>
      walk(l, scope, out); walk(r, scope, out)
    case Expr.UnaryOp(_, op, _) =>
      walk(op, scope, out)
    case Expr.Quantifier(_, bindings, body, _) =>
      var s = scope
      bindings.foreach: b =>
        walk(b.domain, s, out)
        s = s + b.variable
      walk(body, s, out)
    case Expr.SomeWrap(e, _) =>
      walk(e, scope, out)
    case Expr.The(v, d, b, _) =>
      walk(d, scope, out); walk(b, scope + v, out)
    case Expr.FieldAccess(base, _, _) =>
      walk(base, scope, out)
    case Expr.EnumAccess(base, _, _) =>
      walk(base, scope, out)
    case Expr.Index(base, idx, _) =>
      walk(base, scope, out); walk(idx, scope, out)
    case Expr.Call(callee, args, _) =>
      walk(callee, scope, out)
      args.foreach(a => walk(a, scope, out))
    case Expr.Prime(e, _) =>
      walk(e, scope, out)
    case Expr.Pre(e, _) =>
      walk(e, scope, out)
    case Expr.With(base, updates, _) =>
      walk(base, scope, out); updates.foreach(u => walk(u.value, scope, out))
    case Expr.If(c, t, e, _) =>
      walk(c, scope, out); walk(t, scope, out); walk(e, scope, out)
    case Expr.Let(v, value, body, _) =>
      walk(value, scope, out); walk(body, scope + v, out)
    case Expr.Lambda(p, b, _) =>
      walk(b, scope + p, out)
    case Expr.Constructor(_, fields, _) =>
      fields.foreach(f => walk(f.value, scope, out))
    case Expr.SetLiteral(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case Expr.MapLiteral(entries, _) =>
      entries.foreach: e =>
        walk(e.key, scope, out); walk(e.value, scope, out)
    case Expr.SetComprehension(v, d, p, _) =>
      walk(d, scope, out); walk(p, scope + v, out)
    case Expr.SeqLiteral(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case Expr.Matches(e, _, _) =>
      walk(e, scope, out)
    case _: (Expr.IntLit | Expr.FloatLit | Expr.StringLit | Expr.BoolLit | Expr.NoneLit) =>
      ()
