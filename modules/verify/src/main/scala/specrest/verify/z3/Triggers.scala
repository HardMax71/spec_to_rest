package specrest.verify.z3

object Z3Trigger:

  private def freeVarNames(e: Z3Expr): Set[String] = e match
    case Z3Expr.Var(n, _, _) => Set(n)
    case other               => other.children.foldLeft(Set.empty[String])(_ ++ freeVarNames(_))

  // Minimal uninterpreted-function applications covering every bound name: an
  // App is a trigger only if no proper sub-App already covers the bound set,
  // so we pick `store_dom(k)` over `len(store_map(k))`.
  private def minimalCovering(names: Set[String], e: Z3Expr): List[Z3Expr] = e match
    case app @ Z3Expr.App(_, args, _) if names.subsetOf(freeVarNames(app)) =>
      val sub = args.flatMap(minimalCovering(names, _))
      if sub.nonEmpty then sub else List(app)
    case other =>
      other.children.flatMap(minimalCovering(names, _))

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
