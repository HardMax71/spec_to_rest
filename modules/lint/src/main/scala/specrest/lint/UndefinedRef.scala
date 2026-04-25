package specrest.lint

import specrest.ir.Expr
import specrest.ir.ServiceIR

object UndefinedRef extends LintPass:
  val code = "L02"

  private val builtins: Set[String] = Set(
    "true",
    "false",
    "none",
    "len",
    "isValidURI",
    "dom",
    "ran",
    "abs",
    "Int",
    "Long",
    "Float",
    "Double",
    "Bool",
    "Boolean",
    "String",
    "DateTime"
  )

  def run(ir: ServiceIR): List[LintDiagnostic] =
    val out         = List.newBuilder[LintDiagnostic]
    val stateFields = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val entityNames = ir.entities.map(_.name).toSet
    val enumNames   = ir.enums.map(_.name).toSet
    val enumMembers = ir.enums.flatMap(_.values).toSet
    val typeAliases = ir.typeAliases.map(_.name).toSet
    val predicates  = ir.predicates.map(_.name).toSet
    val functions   = ir.functions.map(_.name).toSet
    val global =
      stateFields ++ entityNames ++ enumNames ++ enumMembers ++ typeAliases ++
        predicates ++ functions ++ builtins

    def check(expr: Expr, scope: Set[String]): Unit =
      walk(expr, scope, isCallee = false, out, global)

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

    for a <- ir.typeAliases do
      a.constraint.foreach(check(_, global + "value"))

    for fn <- ir.functions do
      check(fn.body, global ++ fn.params.map(_.name))

    for pr <- ir.predicates do
      check(pr.body, global ++ pr.params.map(_.name))

    out.result()

  private def walk(
      expr: Expr,
      scope: Set[String],
      isCallee: Boolean,
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]],
      global: Set[String]
  ): Unit = expr match
    case Expr.Identifier(name, span) =>
      if !isCallee && !scope.contains(name) then
        out += LintDiagnostic(
          UndefinedRef.code,
          LintLevel.Error,
          s"undefined identifier '$name'",
          span
        )
    case Expr.BinaryOp(_, l, r, _) =>
      walk(l, scope, isCallee = false, out, global)
      walk(r, scope, isCallee = false, out, global)
    case Expr.UnaryOp(_, op, _) =>
      walk(op, scope, isCallee = false, out, global)
    case Expr.Quantifier(_, bindings, body, _) =>
      var s = scope
      bindings.foreach: b =>
        walk(b.domain, s, isCallee = false, out, global)
        s = s + b.variable
      walk(body, s, isCallee = false, out, global)
    case Expr.SomeWrap(e, _) =>
      walk(e, scope, isCallee = false, out, global)
    case Expr.The(v, d, b, _) =>
      walk(d, scope, isCallee = false, out, global)
      walk(b, scope + v, isCallee = false, out, global)
    case Expr.FieldAccess(base, _, _) =>
      walk(base, scope, isCallee = false, out, global)
    case Expr.EnumAccess(base, _, _) =>
      walk(base, scope, isCallee = false, out, global)
    case Expr.Index(base, idx, _) =>
      walk(base, scope, isCallee = false, out, global)
      walk(idx, scope, isCallee = false, out, global)
    case Expr.Call(callee, args, _) =>
      walk(callee, scope, isCallee = true, out, global)
      args.foreach(a => walk(a, scope, isCallee = false, out, global))
    case Expr.Prime(e, _) =>
      walk(e, scope, isCallee = false, out, global)
    case Expr.Pre(e, _) =>
      walk(e, scope, isCallee = false, out, global)
    case Expr.With(base, updates, _) =>
      walk(base, scope, isCallee = false, out, global)
      updates.foreach(u => walk(u.value, scope, isCallee = false, out, global))
    case Expr.If(c, t, e, _) =>
      walk(c, scope, isCallee = false, out, global)
      walk(t, scope, isCallee = false, out, global)
      walk(e, scope, isCallee = false, out, global)
    case Expr.Let(v, value, body, _) =>
      walk(value, scope, isCallee = false, out, global)
      walk(body, scope + v, isCallee = false, out, global)
    case Expr.Lambda(p, b, _) =>
      walk(b, scope + p, isCallee = false, out, global)
    case Expr.Constructor(_, fields, _) =>
      fields.foreach(f => walk(f.value, scope, isCallee = false, out, global))
    case Expr.SetLiteral(elems, _) =>
      elems.foreach(walk(_, scope, isCallee = false, out, global))
    case Expr.MapLiteral(entries, _) =>
      entries.foreach: e =>
        walk(e.key, scope, isCallee = false, out, global)
        walk(e.value, scope, isCallee = false, out, global)
    case Expr.SetComprehension(v, d, p, _) =>
      walk(d, scope, isCallee = false, out, global)
      walk(p, scope + v, isCallee = false, out, global)
    case Expr.SeqLiteral(elems, _) =>
      elems.foreach(walk(_, scope, isCallee = false, out, global))
    case Expr.Matches(e, _, _) =>
      walk(e, scope, isCallee = false, out, global)
    case _: (Expr.IntLit | Expr.FloatLit | Expr.StringLit | Expr.BoolLit | Expr.NoneLit) =>
      ()
