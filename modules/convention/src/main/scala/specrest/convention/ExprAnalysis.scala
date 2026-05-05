package specrest.convention

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IsInstanceOf"))
object ExprAnalysis:

  enum WalkAction derives CanEqual:
    case Continue, Skip

  def walkExpr(expr: expr_full, visit: expr_full => WalkAction): Unit =
    if visit(expr) == WalkAction.Skip then ()
    else
      expr match
        case BinaryOpF(_, l, r, _) =>
          walkExpr(l, visit); walkExpr(r, visit)
        case UnaryOpF(_, op, _) =>
          walkExpr(op, visit)
        case QuantifierF(_, bindings, body, _) =>
          bindings.foreach(b => walkExpr(b.domain, visit))
          walkExpr(body, visit)
        case SomeWrapF(e, _) =>
          walkExpr(e, visit)
        case TheF(_, d, b, _) =>
          walkExpr(d, visit); walkExpr(b, visit)
        case FieldAccessF(base, _, _) =>
          walkExpr(base, visit)
        case EnumAccessF(base, _, _) =>
          walkExpr(base, visit)
        case IndexF(base, idx, _) =>
          walkExpr(base, visit); walkExpr(idx, visit)
        case CallF(callee, args, _) =>
          walkExpr(callee, visit); args.foreach(walkExpr(_, visit))
        case PrimeF(e, _) =>
          walkExpr(e, visit)
        case PreF(e, _) =>
          walkExpr(e, visit)
        case WithF(base, updates, _) =>
          walkExpr(base, visit); updates.foreach(u => walkExpr(u.value, visit))
        case IfF(c, t, e, _) =>
          walkExpr(c, visit); walkExpr(t, visit); walkExpr(e, visit)
        case LetF(_, v, b, _) =>
          walkExpr(v, visit); walkExpr(b, visit)
        case LambdaF(_, b, _) =>
          walkExpr(b, visit)
        case ConstructorF(_, fields, _) =>
          fields.foreach(f => walkExpr(f.value, visit))
        case SetLiteralF(elems, _) =>
          elems.foreach(walkExpr(_, visit))
        case MapLiteralF(entries, _) =>
          entries.foreach { e =>
            walkExpr(e.key, visit); walkExpr(e.value, visit)
          }
        case SetComprehensionF(_, d, p, _) =>
          walkExpr(d, visit); walkExpr(p, visit)
        case SeqLiteralF(elems, _) =>
          elems.foreach(walkExpr(_, visit))
        case MatchesF(e, _, _) =>
          walkExpr(e, visit)
        case _: (IdentifierF | IntLitF | FloatLitF | StringLitF |
              BoolLitF | NoneLitF) =>
          ()

  private def rootIdentifier(expr: expr_full): Option[String] = expr match
    case IdentifierF(name, _)     => Some(name)
    case IndexF(base, _, _)       => rootIdentifier(base)
    case FieldAccessF(base, _, _) => rootIdentifier(base)
    case _                        => None

  def collectPrimedIdentifiers(ensures: List[expr_full]): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- ensures do
      walkExpr(
        clause,
        (node: expr_full) =>
          node match
            case PrimeF(inner, _) =>
              rootIdentifier(inner).foreach(result += _)
              WalkAction.Continue
            case _ => WalkAction.Continue
      )
    result.toSet

  def collectPreservedRelations(
      ensures: List[expr_full],
      stateFieldNames: Set[String]
  ): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- flattenEnsures(ensures) do
      clause match
        case BinaryOpF(BEq(), PrimeF(IdentifierF(l, _), _), IdentifierF(r, _), _)
            if l == r && stateFieldNames.contains(l) =>
          result += l
        case _ => ()
    result.toSet

  final case class CreatePattern(field: String)

  def detectCreatePattern(
      ensures: List[expr_full],
      stateFieldNames: Set[String]
  ): Option[CreatePattern] =
    flattenEnsures(ensures).collectFirst {
      case BinaryOpF(
            BEq(),
            PrimeF(IdentifierF(name, _), _),
            rhs @ BinaryOpF(BAdd(), _, _, _),
            _
          ) if stateFieldNames.contains(name) && containsPreInPlusChain(rhs, name) =>
        CreatePattern(name)
    }

  private def containsPreInPlusChain(expr: expr_full, fieldName: String): Boolean = expr match
    case PreF(IdentifierF(n, _), _) => n == fieldName
    case BinaryOpF(BAdd(), l, r, _) =>
      containsPreInPlusChain(l, fieldName) || containsPreInPlusChain(r, fieldName)
    case _ => false

  final case class DeletePattern(field: String)

  def detectDeletePattern(
      ensures: List[expr_full],
      stateFieldNames: Set[String]
  ): Option[DeletePattern] =
    flattenEnsures(ensures).collectFirst {
      case BinaryOpF(BNotIn(), _, PrimeF(IdentifierF(n, _), _), _)
          if stateFieldNames.contains(n) =>
        DeletePattern(n)
    }

  final case class WithInfo(fieldNames: List[String], baseIdentifier: Option[String])

  def collectWithFields(ensures: List[expr_full]): Option[WithInfo] =
    ensures.view.flatMap(findWithIn).headOption

  private def findWithIn(clause: expr_full): Option[WithInfo] =
    var found: Option[WithInfo] = None
    walkExpr(
      clause,
      (node: expr_full) =>
        if found.isDefined then WalkAction.Skip
        else
          node match
            case WithF(base, updates, _) =>
              found = Some(WithInfo(updates.map(_.name), resolveWithBase(base)))
              WalkAction.Skip
            case _ => WalkAction.Continue
    )
    found

  private def resolveWithBase(expr: expr_full): Option[String] = expr match
    case IdentifierF(_, _) => None
    case IndexF(base, _, _) =>
      base match
        case PreF(IdentifierF(n, _), _) => Some(n)
        case IdentifierF(n, _)          => Some(n)
        case _                          => rootIdentifier(base)
    case other => rootIdentifier(other)

  def countFilterParams(inputs: List[param_decl_full]): Int =
    inputs.count(_.typeExpr.isInstanceOf[OptionTypeF])

  def hasCollectionInput(inputs: List[param_decl_full]): Boolean =
    inputs.exists: p =>
      p.typeExpr.isInstanceOf[SetTypeF]
        || p.typeExpr.isInstanceOf[SeqTypeF]
        || p.typeExpr.isInstanceOf[MapTypeF]

  def flattenEnsures(ensures: List[expr_full]): List[expr_full] =
    val out = List.newBuilder[expr_full]
    ensures.foreach(clause => flattenExpr(clause, out))
    out.result()

  private def flattenExpr(
      expr: expr_full,
      out: scala.collection.mutable.Builder[expr_full, List[expr_full]]
  ): Unit =
    expr match
      case BinaryOpF(BAnd(), l, r, _) =>
        flattenExpr(l, out); flattenExpr(r, out)
      case LetF(_, v, b, _) =>
        flattenExpr(v, out); flattenExpr(b, out)
      case other => out += other

  def detectKeyExistsInRequires(
      requires: List[expr_full],
      stateFieldNames: Set[String]
  ): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- flattenEnsures(requires) do
      clause match
        case BinaryOpF(BIn(), _, IdentifierF(n, _), _)
            if stateFieldNames.contains(n) =>
          result += n
        case _ => ()
    result.toSet
