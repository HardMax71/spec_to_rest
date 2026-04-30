package specrest.convention

import specrest.ir.*

@SuppressWarnings(Array("org.wartremover.warts.Var", "org.wartremover.warts.IsInstanceOf"))
object ExprAnalysis:

  enum WalkAction derives CanEqual:
    case Continue, Skip

  def walkExpr(expr: Expr, visit: Expr => WalkAction): Unit =
    if visit(expr) == WalkAction.Skip then ()
    else
      expr match
        case Expr.BinaryOp(_, l, r, _) =>
          walkExpr(l, visit); walkExpr(r, visit)
        case Expr.UnaryOp(_, op, _) =>
          walkExpr(op, visit)
        case Expr.Quantifier(_, bindings, body, _) =>
          bindings.foreach(b => walkExpr(b.domain, visit))
          walkExpr(body, visit)
        case Expr.SomeWrap(e, _) =>
          walkExpr(e, visit)
        case Expr.The(_, d, b, _) =>
          walkExpr(d, visit); walkExpr(b, visit)
        case Expr.FieldAccess(base, _, _) =>
          walkExpr(base, visit)
        case Expr.EnumAccess(base, _, _) =>
          walkExpr(base, visit)
        case Expr.Index(base, idx, _) =>
          walkExpr(base, visit); walkExpr(idx, visit)
        case Expr.Call(callee, args, _) =>
          walkExpr(callee, visit); args.foreach(walkExpr(_, visit))
        case Expr.Prime(e, _) =>
          walkExpr(e, visit)
        case Expr.Pre(e, _) =>
          walkExpr(e, visit)
        case Expr.With(base, updates, _) =>
          walkExpr(base, visit); updates.foreach(u => walkExpr(u.value, visit))
        case Expr.If(c, t, e, _) =>
          walkExpr(c, visit); walkExpr(t, visit); walkExpr(e, visit)
        case Expr.Let(_, v, b, _) =>
          walkExpr(v, visit); walkExpr(b, visit)
        case Expr.Lambda(_, b, _) =>
          walkExpr(b, visit)
        case Expr.Constructor(_, fields, _) =>
          fields.foreach(f => walkExpr(f.value, visit))
        case Expr.SetLiteral(elems, _) =>
          elems.foreach(walkExpr(_, visit))
        case Expr.MapLiteral(entries, _) =>
          entries.foreach { e =>
            walkExpr(e.key, visit); walkExpr(e.value, visit)
          }
        case Expr.SetComprehension(_, d, p, _) =>
          walkExpr(d, visit); walkExpr(p, visit)
        case Expr.SeqLiteral(elems, _) =>
          elems.foreach(walkExpr(_, visit))
        case Expr.Matches(e, _, _) =>
          walkExpr(e, visit)
        case _: (Expr.Identifier | Expr.IntLit | Expr.FloatLit | Expr.StringLit |
              Expr.BoolLit | Expr.NoneLit) =>
          ()

  private def rootIdentifier(expr: Expr): Option[String] = expr match
    case Expr.Identifier(name, _)     => Some(name)
    case Expr.Index(base, _, _)       => rootIdentifier(base)
    case Expr.FieldAccess(base, _, _) => rootIdentifier(base)
    case _                            => None

  def collectPrimedIdentifiers(ensures: List[Expr]): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- ensures do
      walkExpr(
        clause,
        (node: Expr) =>
          node match
            case Expr.Prime(inner, _) =>
              rootIdentifier(inner).foreach(result += _)
              WalkAction.Continue
            case _ => WalkAction.Continue
      )
    result.toSet

  def collectPreservedRelations(
      ensures: List[Expr],
      stateFieldNames: Set[String]
  ): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- flattenEnsures(ensures) do
      clause match
        case Expr.BinaryOp(BinOp.Eq, Expr.Prime(Expr.Identifier(l, _), _), Expr.Identifier(r, _), _)
            if l == r && stateFieldNames.contains(l) =>
          result += l
        case _ => ()
    result.toSet

  final case class CreatePattern(field: String)

  def detectCreatePattern(
      ensures: List[Expr],
      stateFieldNames: Set[String]
  ): Option[CreatePattern] =
    flattenEnsures(ensures).collectFirst {
      case Expr.BinaryOp(
            BinOp.Eq,
            Expr.Prime(Expr.Identifier(name, _), _),
            rhs @ Expr.BinaryOp(BinOp.Add, _, _, _),
            _
          ) if stateFieldNames.contains(name) && containsPreInPlusChain(rhs, name) =>
        CreatePattern(name)
    }

  private def containsPreInPlusChain(expr: Expr, fieldName: String): Boolean = expr match
    case Expr.Pre(Expr.Identifier(n, _), _) => n == fieldName
    case Expr.BinaryOp(BinOp.Add, l, r, _) =>
      containsPreInPlusChain(l, fieldName) || containsPreInPlusChain(r, fieldName)
    case _ => false

  final case class DeletePattern(field: String)

  def detectDeletePattern(
      ensures: List[Expr],
      stateFieldNames: Set[String]
  ): Option[DeletePattern] =
    flattenEnsures(ensures).collectFirst {
      case Expr.BinaryOp(BinOp.NotIn, _, Expr.Prime(Expr.Identifier(n, _), _), _)
          if stateFieldNames.contains(n) =>
        DeletePattern(n)
    }

  final case class WithInfo(fieldNames: List[String], baseIdentifier: Option[String])

  def collectWithFields(ensures: List[Expr]): Option[WithInfo] =
    ensures.view.flatMap(findWithIn).headOption

  private def findWithIn(clause: Expr): Option[WithInfo] =
    var found: Option[WithInfo] = None
    walkExpr(
      clause,
      (node: Expr) =>
        if found.isDefined then WalkAction.Skip
        else
          node match
            case Expr.With(base, updates, _) =>
              found = Some(WithInfo(updates.map(_.name), resolveWithBase(base)))
              WalkAction.Skip
            case _ => WalkAction.Continue
    )
    found

  private def resolveWithBase(expr: Expr): Option[String] = expr match
    case Expr.Identifier(_, _) => None
    case Expr.Index(base, _, _) =>
      base match
        case Expr.Pre(Expr.Identifier(n, _), _) => Some(n)
        case Expr.Identifier(n, _)              => Some(n)
        case _                                  => rootIdentifier(base)
    case other => rootIdentifier(other)

  def countFilterParams(inputs: List[ParamDecl]): Int =
    inputs.count(_.typeExpr.isInstanceOf[TypeExpr.OptionType])

  def hasCollectionInput(inputs: List[ParamDecl]): Boolean =
    inputs.exists: p =>
      p.typeExpr.isInstanceOf[TypeExpr.SetType]
        || p.typeExpr.isInstanceOf[TypeExpr.SeqType]
        || p.typeExpr.isInstanceOf[TypeExpr.MapType]

  def flattenEnsures(ensures: List[Expr]): List[Expr] =
    val out = List.newBuilder[Expr]
    ensures.foreach(clause => flattenExpr(clause, out))
    out.result()

  private def flattenExpr(
      expr: Expr,
      out: scala.collection.mutable.Builder[Expr, List[Expr]]
  ): Unit =
    expr match
      case Expr.BinaryOp(BinOp.And, l, r, _) =>
        flattenExpr(l, out); flattenExpr(r, out)
      case Expr.Let(_, v, b, _) =>
        flattenExpr(v, out); flattenExpr(b, out)
      case other => out += other

  def detectKeyExistsInRequires(
      requires: List[Expr],
      stateFieldNames: Set[String]
  ): Set[String] =
    val result = scala.collection.mutable.Set.empty[String]
    for clause <- flattenEnsures(requires) do
      clause match
        case Expr.BinaryOp(BinOp.In, _, Expr.Identifier(n, _), _)
            if stateFieldNames.contains(n) =>
          result += n
        case _ => ()
    result.toSet
