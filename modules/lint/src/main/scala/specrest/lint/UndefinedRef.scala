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
    val out = List.newBuilder[LintDiagnostic]
    val stateFields = ir.f.toList.flatMap {
      case StateDeclFull(fs, _) => fs.map { case StateFieldDeclFull(n, _, _) => n }
    }.toSet
    val entityNames  = ir.c.map { case EntityDeclFull(n, _, _, _, _) => n }.toSet
    val enumNames    = ir.d.map { case EnumDeclFull(n, _, _) => n }.toSet
    val enumMembers  = ir.d.flatMap { case EnumDeclFull(_, vs, _) => vs }.toSet
    val typeAliases  = ir.e.map { case TypeAliasDeclFull(n, _, _, _) => n }.toSet
    val predicates   = ir.m.map { case PredicateDeclFull(n, _, _, _) => n }.toSet
    val functions    = ir.l.map { case FunctionDeclFull(n, _, _, _, _) => n }.toSet
    val factImplicit = ir.k.flatMap { case FactDeclFull(_, e, _) => collectCallees(e) }.toSet
    val global =
      stateFields ++ entityNames ++ enumNames ++ enumMembers ++ typeAliases ++
        predicates ++ functions ++ builtins ++ factImplicit

    def check(expr: expr_full, scope: Set[String]): Unit =
      walk(expr, scope, out)

    for case OperationDeclFull(_, inputs, outputs, requires, ensures, _) <- ir.g do
      val opScope = global ++ inputs.map { case ParamDeclFull(n, _, _) => n } ++
        outputs.map { case ParamDeclFull(n, _, _) => n } + "self"
      requires.foreach(check(_, opScope))
      ensures.foreach(check(_, opScope))

    ir.i.foreach { case InvariantDeclFull(_, e, _) => check(e, global + "self") }
    ir.j.foreach { case TemporalDeclFull(_, e, _) => check(e, global + "self") }
    ir.k.foreach { case FactDeclFull(_, e, _) => check(e, global + "self") }

    for case EntityDeclFull(_, _, fields, invs, _) <- ir.c do
      val entScope = global + "self" + "value" ++ fields.map { case FieldDeclFull(n, _, _, _) => n }
      fields.foreach { case FieldDeclFull(_, _, c, _) => c.foreach(check(_, entScope)) }
      invs.foreach(check(_, entScope))

    for case TypeAliasDeclFull(_, _, c, _) <- ir.e do c.foreach(check(_, global + "value"))

    for case FunctionDeclFull(_, params, _, body, _) <- ir.l do
      check(body, global ++ params.map { case ParamDeclFull(n, _, _) => n })

    for case PredicateDeclFull(_, params, body, _) <- ir.m do
      check(body, global ++ params.map { case ParamDeclFull(n, _, _) => n })

    for case TransitionDeclFull(_, entityName, _, rules, _) <- ir.h do
      val entFields = ir.c.collect {
        case EntityDeclFull(n, _, fs, _, _) if n == entityName =>
          fs.map { case FieldDeclFull(fn, _, _, _) => fn }.toSet
      }.headOption.getOrElse(Set.empty)
      val trScope = global ++ entFields + "self"
      rules.foreach { case TransitionRuleFull(_, _, _, guard, _) =>
        guard.foreach(check(_, trScope))
      }

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
      bindings.foreach { case QuantifierBindingFull(v, dom, _, _) =>
        walk(dom, s, out)
        s = s + v
      }
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
      walk(base, scope, out)
      updates.foreach { case FieldAssignFull(_, v, _) => walk(v, scope, out) }
    case IfF(c, t, e, _) =>
      walk(c, scope, out); walk(t, scope, out); walk(e, scope, out)
    case LetF(v, value, body, _) =>
      walk(value, scope, out); walk(body, scope + v, out)
    case LambdaF(p, b, _) =>
      walk(b, scope + p, out)
    case ConstructorF(_, fields, _) =>
      fields.foreach { case FieldAssignFull(_, v, _) => walk(v, scope, out) }
    case SetLiteralF(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case MapLiteralF(entries, _) =>
      entries.foreach { case MapEntryFull(k, v, _) =>
        walk(k, scope, out); walk(v, scope, out)
      }
    case SetComprehensionF(v, d, p, _) =>
      walk(d, scope, out); walk(p, scope + v, out)
    case SeqLiteralF(elems, _) =>
      elems.foreach(walk(_, scope, out))
    case MatchesF(e, _, _) =>
      walk(e, scope, out)
    case _: (IntLitF | FloatLitF | StringLitF | BoolLitF | NoneLitF) =>
      ()
