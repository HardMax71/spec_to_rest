package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

private[z3] trait Z3EncodingSupport:

  private[z3] def withStateMode[T](ctx: TranslateCtx, mode: StateMode, fn: () => T): T =
    val saved = ctx.stateMode
    ctx.stateMode = mode
    try fn()
    finally ctx.stateMode = saved

  private[z3] def peelRelationRef(t: smt_term, default: StateMode): Option[(String, StateMode)] =
    peelSmtRelationRef(t).map: rel =>
      val mode = t match
        case TPre(_)   => StateMode.Pre
        case TPrime(_) => StateMode.Post
        case _         => default
      (rel, mode)

  private[z3] def domFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.domFuncPost else info.domFunc

  private[z3] def mapFuncFor(info: StateRelationInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.mapFuncPost else info.mapFunc

  private[z3] def constFuncFor(info: StateConstInfo, mode: StateMode): String =
    if mode == StateMode.Post then info.funcNamePost else info.funcName

  private[z3] def sortNameOf(s: Z3Sort): String = s match
    case Z3Sort.Int         => "Int"
    case Z3Sort.Real        => "Real"
    case Z3Sort.Bool        => "Bool"
    case Z3Sort.Uninterp(n) => n
    case Z3Sort.SetOf(e)    => s"Set_${sortNameOf(e)}"
    case Z3Sort.OptionOf(e) => s"Option_${sortNameOf(e)}"
    case Z3Sort.SeqOf(e)    => s"Seq_${sortNameOf(e)}"
    case Z3Sort.MapOf(k, v) => s"Map_${sortNameOf(k)}_${sortNameOf(v)}"
    case Z3Sort.Str         => "String"

  private[z3] def coerceOptionalEquality(
      ctx: TranslateCtx,
      left: Z3Expr,
      right: Z3Expr
  ): (Z3Expr, Z3Expr) =
    (inferSortOfZ3Expr(ctx, left), inferSortOfZ3Expr(ctx, right)) match
      case (Some(Z3Sort.OptionOf(elem)), Some(rs)) if Z3Sort.eq(rs, elem) =>
        (left, Z3Expr.OptSome(right))
      case (Some(ls), Some(Z3Sort.OptionOf(elem))) if Z3Sort.eq(ls, elem) =>
        (Z3Expr.OptSome(left), right)
      case _ => (left, right)

  private[z3] def cardinalityRefFor(
      ctx: TranslateCtx,
      targetName: String,
      mode: StateMode
  ): Z3Expr =
    ctx.state.get(targetName) match
      case Some(_: StateRelationInfo) =>
        val funcName = ctx.cardinalityNameFor(targetName, mode)
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Int))
          ctx.assertions += Z3Expr.Cmp(
            CmpOp.Ge,
            Z3Expr.App(funcName, Nil),
            Z3Expr.IntLit(BigInt(0))
          )
        Z3Expr.App(funcName, Nil)
      case _ =>
        fail(
          ctx,
          s"cardinality '#$targetName' requires a state relation; '$targetName' is not declared as one"
        )

  private[z3] def entitySetCardinality(ctx: TranslateCtx, setExpr: Z3Expr): Z3Expr =
    inferSortOfZ3Expr(ctx, setExpr) match
      case Some(Z3Sort.SetOf(elem)) =>
        val funcName = s"setCard_${sortNameOf(elem)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(Z3Sort.SetOf(elem)), Z3Sort.Int))
          ctx.assertions += Z3Expr.Quantifier(
            QKind.ForAll,
            List(Z3Binding("setcard_s", Z3Sort.SetOf(elem))),
            Z3Expr.Cmp(
              CmpOp.Ge,
              Z3Expr.App(funcName, List(Z3Expr.Var("setcard_s", Z3Sort.SetOf(elem)))),
              Z3Expr.IntLit(BigInt(0))
            )
          )
        Z3Expr.App(funcName, List(setExpr))
      case _ =>
        fail(ctx, "cardinality '#' on a non-relation operand requires a set-typed value")

  private[z3] def resolveIdentifier(
      ctx: TranslateCtx,
      name: String,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    env.get(name) match
      case Some(bound) => bound
      case None =>
        ctx.state.get(name) match
          case Some(c: StateConstInfo) =>
            Z3Expr.App(constFuncFor(c, ctx.stateMode), Nil)
          case Some(r: StateRelationInfo) =>
            val suffix  = if ctx.stateMode == StateMode.Post then "_post" else ""
            val refName = s"state_${name}_ref$suffix"
            if !ctx.funcs.contains(refName) then
              val s = Z3Sort.Uninterp(s"Rel_${sortNameOf(r.keySort)}_${sortNameOf(r.valueSort)}")
              ctx.declareFunc(Z3FunctionDecl(refName, Nil, s))
            Z3Expr.App(refName, Nil)
          case None =>
            ctx.entities.get(name) match
              case Some(entity) =>
                val funcName = s"entity_${name}_ref"
                if !ctx.funcs.contains(funcName) then
                  ctx.declareFunc(Z3FunctionDecl(funcName, Nil, entity.sort))
                Z3Expr.App(funcName, Nil)
              case None =>
                enumMemberConst(ctx, name).getOrElse:
                  val funcName = s"id_$name"
                  if !ctx.funcs.contains(funcName) then
                    ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Uninterp("Any")))
                  Z3Expr.App(funcName, Nil)

  private[z3] def enumMemberConst(ctx: TranslateCtx, name: String): Option[Z3Expr] =
    ctx.enums.toList.filter((_, info) => info.members.contains(name)) match
      case (enumName, info) :: Nil =>
        val funcName = s"${enumName}_$name"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, info.sort))
        Some(Z3Expr.App(funcName, Nil))
      case Nil => None
      case matches =>
        fail(
          ctx,
          s"bare enum member '$name' is ambiguous (declared by ${matches.map(_._1).mkString(", ")}); qualify it as 'Enum.$name'"
        )

  private[z3] def inferSortOfZ3Expr(ctx: TranslateCtx, e: Z3Expr): Option[Z3Sort] = e match
    case Z3Expr.Var(_, sort, _)  => Some(sort)
    case Z3Expr.IntLit(_, _)     => Some(Z3Sort.Int)
    case Z3Expr.RealLit(_, _, _) => Some(Z3Sort.Real)
    case Z3Expr.BoolLit(_, _)    => Some(Z3Sort.Bool)
    case Z3Expr.App(func, _, _)  => ctx.funcs.get(func).map(_.resultSort)
    case Z3Expr.And(_, _) | Z3Expr.Or(_, _) | Z3Expr.Not(_, _) | Z3Expr.Implies(_, _, _) |
        Z3Expr.Cmp(_, _, _, _) | Z3Expr.StrCmp(_, _, _, _) | Z3Expr.Quantifier(_, _, _, _) =>
      Some(Z3Sort.Bool)
    case Z3Expr.Arith(_, args, _) =>
      val argSorts = args.map(inferSortOfZ3Expr(ctx, _))
      if argSorts.nonEmpty && argSorts.forall(_.exists(Z3Sort.isNumeric)) then
        if argSorts.exists(_.contains(Z3Sort.Real)) then Some(Z3Sort.Real) else Some(Z3Sort.Int)
      else None
    case Z3Expr.EmptySet(s, _)     => Some(Z3Sort.SetOf(s))
    case Z3Expr.SetLit(s, _, _)    => Some(Z3Sort.SetOf(s))
    case Z3Expr.SetMember(_, _, _) => Some(Z3Sort.Bool)
    case Z3Expr.SetBinOp(op, l, _, _) =>
      op match
        case SetOpKind.Subset                                       => Some(Z3Sort.Bool)
        case SetOpKind.Union | SetOpKind.Intersect | SetOpKind.Diff => inferSortOfZ3Expr(ctx, l)
    case Z3Expr.Ite(_, t, e, _) =>
      (inferSortOfZ3Expr(ctx, t), inferSortOfZ3Expr(ctx, e)) match
        case (Some(ts), Some(es)) => if Z3Sort.eq(ts, es) then Some(ts) else None
        case (ts, es)             => ts.orElse(es)
    case Z3Expr.OptNone(elemSort, _) => Some(Z3Sort.OptionOf(elemSort))
    case Z3Expr.OptSome(value, _)    => inferSortOfZ3Expr(ctx, value).map(Z3Sort.OptionOf.apply)
    case Z3Expr.OptGet(value, _) =>
      inferSortOfZ3Expr(ctx, value) match
        case Some(Z3Sort.OptionOf(e)) => Some(e)
        case _                        => None
    case Z3Expr.StrLit(_, _)           => Some(Z3Sort.Str)
    case Z3Expr.StrConcat(_, _, _)     => Some(Z3Sort.Str)
    case Z3Expr.SeqConcat(l, _, _)     => inferSortOfZ3Expr(ctx, l)
    case Z3Expr.SeqContains(_, _, _)   => Some(Z3Sort.Bool)
    case Z3Expr.InRe(_, _, _)          => Some(Z3Sort.Bool)
    case Z3Expr.SeqLit(elemSort, _, _) => Some(Z3Sort.SeqOf(elemSort))
    case Z3Expr.MapLit(keySort, valueSort, _, _) =>
      Some(Z3Sort.MapOf(keySort, valueSort))
