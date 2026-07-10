package specrest.verify.z3

object Z3Trigger:

  private def minimalCovering(
      names: Set[String],
      innerBound: Set[String],
      e: Z3Expr
  ): List[Z3Expr] =
    e match
      // The minimal covering App, and never one that mentions a name bound by an
      // inner quantifier (`innerBound`): a legal E-matching pattern cannot. The
      // sub-App preference then picks `store_dom(k)` over `len(store_map(k))`.
      case app @ Z3Expr.App(_, args, _)
          if names.subsetOf(app.freeVars) && app.freeVars.intersect(innerBound).isEmpty =>
        val sub = args.flatMap(minimalCovering(names, innerBound, _))
        if sub.nonEmpty then sub else List(app)
      case Z3Expr.Quantifier(_, bs, body, _) =>
        minimalCovering(names, innerBound ++ bs.map(_.name), body)
      case other =>
        other.children.flatMap(minimalCovering(names, innerBound, _))

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
        case Z3Expr.Implies(g, _, _) => minimalCovering(names, Set.empty, g)
        case _                       => Nil
      val cands = if fromGuard.nonEmpty then fromGuard else minimalCovering(names, Set.empty, body)
      cands.distinct
