package specrest.lint

import specrest.ir.generated.SpecRestGenerated.*

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

  def run(ir: ServiceIRFull): List[LintDiagnostic] =
    val out          = List.newBuilder[LintDiagnostic]
    val stateFields  = ir.state.toList.flatMap(_.fields.map(_.name)).toSet
    val entityNames  = ir.c.map(_.name).toSet
    val enumNames    = ir.d.map(_.name).toSet
    val enumMembers  = ir.d.flatMap(_.values).toSet
    val e = ir.e.map(_.name).toSet
    val m = ir.m.map(_.name).toSet
    val l = ir.l.map(_.name).toSet
    val factImplicit = ir.k.flatMap(f => collectCallees(f.expr)).toSet
    val global =
      stateFields ++ entityNames ++ enumNames ++ enumMembers ++ typeAliases ++
        predicates ++ functions ++ builtins ++ factImplicit

    def check(expr: expr_full, scope: Set[String]): Unit =
      walk(expr, scope, out)

    for op <- ir.g do
      val opScope = global ++ op.b.map(_.name) ++ op.c.map(_.name) + "self"
      op.d.foreach(check(_, opScope))
      op.e.foreach(check(_, opScope))

    ir.invariants.foreach(i => check(i.expr, global + "self"))
    ir.j.foreach(t => check(t.expr, global + "self"))
    ir.k.foreach(f => check(f.expr, global + "self"))

    for ent <- ir.c do
      val entScope = global + "self" + "value" ++ ent.fields.map(_.name)
      ent.fields.foreach(_.c.foreach(check(_, entScope)))
      ent.invariants.foreach(check(_, entScope))

    for a <- ir.e do a.c.foreach(check(_, global + "value"))

    for fn <- ir.l do check(fn.body, global ++ fn.params.map(_.name))

    for pr <- ir.m do check(pr.body, global ++ pr.params.map(_.name))

    for tr <- ir.h do
      val entFields = ir.c
        .find(_.name == tr.b)
        .map(_.fields.map(_.name).toSet)
        .getOrElse(Set.empty)
      val trScope = global ++ entFields + "self"
      tr.rules.foreach(_.d.foreach(check(_, trScope)))

    out.result()

  private def collectCallees(e: expr_full): List[String] =
    val acc = scala.collection.mutable.ListBuffer.empty[String]
    ExprWalk.foreach(e):
      case CallF(IdentifierF(n, _), _, _) => acc += n
      case _                              => ()
    acc.toList

  @SuppressWarnings(Array("org.wartremover.warts.Var"))
  private def walk(
      expr: expr_full,
      scope: Set[String],
      out: scala.collection.mutable.Builder[LintDiagnostic, List[LintDiagnostic]]
  ): Unit = expr match
    case IdentifierF(name, span) =>
      if !scope.contains(name) then
        out += LintDiagnostic(
          UndefinedRef.code,
          LintLevel.Error,
          s"undefined identifier '$name'",
          span
        )
    case BinaryOpF(_, l, r, _) =>
      walk(l, scope, out); walk(r, scope, out)
    case UnaryOpF(_, op, _) =>
      walk(op, scope, out)
    case QuantifierF(_, bindings, body, _) =>
      var s = scope
      bindings.foreach: b =>
        walk(b.domain, s, out)
        s = s + b.a
      walk(body, s, out)
    case SomeWrapF(e, _) =>
      walk(e, scope, out)
    case TheF(v, d, b, _) =>
      walk(d, scope, out); walk(b, scope + v, out)
    case FieldAccessF(base, _, _) =>
      walk(base, scope, out)
    case EnumAccessF(base, _, _) =>
      walk(base, scope, out)
    case IndexF(base, idx, _) =>
      walk(base, scope, out); walk(idx, scope, out)
    case CallF(callee, args, _) =>
      walk(callee, scope, out)
      args.foreach(a => walk(a, scope, out))
    case PrimeF(e, _) =>
      walk(e, scope, out)
    case PreF(e, _) =>
      walk(e, scope, out)
    case WithF(base, updates, _) =>
      walk(base, scope, out); updates.foreach(u => walk(u.value, scope, out))
    case IfF(c, t, e, _) =>
      walk(c, scope, out); walk(t, scope, out); walk(e, scope, out)
    case LetF(v, value, body, _) =>
      walk(value, scope, out); walk(body, scope + v, out)
    case LambdaF(p, b, _) =>
      walk(b, scope + p, out)
    case ConstructorF(_, fields, _) =>
      fields.foreach(f => walk(f.value, scope, out))
    case SetLiteralF(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case MapLiteralF(entries, _) =>
      entries.foreach: e =>
        walk(e.key, scope, out); walk(e.value, scope, out)
    case SetComprehensionF(v, d, p, _) =>
      walk(d, scope, out); walk(p, scope + v, out)
    case SeqLiteralF(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case MatchesF(e, _, _) =>
      walk(e, scope, out)
    case _: (IntLitF | FloatLitF | StringLitF | BoolLitF | NoneLitF) =>
      ()
