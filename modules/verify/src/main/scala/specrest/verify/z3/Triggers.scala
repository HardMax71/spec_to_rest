package specrest.verify.z3

object Z3Trigger:

  private def children(e: Z3Expr): List[Z3Expr] = e match
    case Z3Expr.App(_, args, _)        => args
    case Z3Expr.And(args, _)           => args
    case Z3Expr.Or(args, _)            => args
    case Z3Expr.Not(a, _)              => List(a)
    case Z3Expr.Implies(l, r, _)       => List(l, r)
    case Z3Expr.Cmp(_, l, r, _)        => List(l, r)
    case Z3Expr.StrCmp(_, l, r, _)     => List(l, r)
    case Z3Expr.StrConcat(l, r, _)     => List(l, r)
    case Z3Expr.Arith(_, args, _)      => args
    case Z3Expr.Quantifier(_, _, b, _) => List(b)
    case Z3Expr.SetLit(_, ms, _)       => ms
    case Z3Expr.SetMember(el, s, _)    => List(el, s)
    case Z3Expr.SetBinOp(_, l, r, _)   => List(l, r)
    case Z3Expr.Ite(c, t, el, _)       => List(c, t, el)
    case Z3Expr.OptSome(v, _)          => List(v)
    case Z3Expr.InRe(s, _, _)          => List(s)
    case Z3Expr.SeqLit(_, ms, _)       => ms
    case Z3Expr.MapLit(_, _, es, _)    => es.flatMap((k, v) => List(k, v))
    case _                             => Nil

  private def freeVarNames(e: Z3Expr): Set[String] = e match
    case Z3Expr.Var(n, _, _) => Set(n)
    case other               => children(other).foldLeft(Set.empty[String])(_ ++ freeVarNames(_))

  // Minimal uninterpreted-function applications covering every bound name: an
  // App is a trigger only if no proper sub-App already covers the bound set,
  // so we pick `store_dom(k)` over `len(store_map(k))`.
  private def minimalCovering(names: Set[String], e: Z3Expr): List[Z3Expr] = e match
    case app @ Z3Expr.App(_, args, _) if names.subsetOf(freeVarNames(app)) =>
      val sub = args.flatMap(minimalCovering(names, _))
      if sub.nonEmpty then sub else List(app)
    case other =>
      children(other).flatMap(minimalCovering(names, _))

  // E-matching patterns for a quantifier body. Universals over native theory
  // sorts (String) with no patterns drive Z3 to MBQI, which cannot enumerate
  // the infinite domain and times out; explicit patterns restrict instantiation
  // to the ground terms in the goal. A guarded body `dom(k) => P` triggers on
  // the guard's relation lookup; otherwise on the minimal covering apps in P.
  def infer(bindings: List[Z3Binding], body: Z3Expr): List[Z3Expr] =
    val names = bindings.map(_.name).toSet
    if names.isEmpty then Nil
    else
      val fromGuard = body match
        case Z3Expr.Implies(g, _, _) => minimalCovering(names, g)
        case _                       => Nil
      val cands = if fromGuard.nonEmpty then fromGuard else minimalCovering(names, body)
      cands.distinct
