package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*

// Per-operation hydration scopes: which rows of each state relation a kernel
// request must load before the Requires twin and the verified body run.
//
// The verified body satisfies its ensures for EVERY state meeting the
// requires, and a partially hydrated state is just another state, so ensures
// need no rows of their own (identity frames and cardinality claims are
// discharged statically). What must be present at runtime:
//   - every row the Requires twin evaluates (indexed or membership reads),
//   - every row the body reads to construct its updates (keyed pre() reads
//     in the ensures name exactly those),
//   - whole relations wherever a reference is not keyed by a value known
//     before the call: content searches, candidate-freshness not-in checks
//     (the candidate samples after hydration), and quantifier scans.
// Anything unrecognized falls open to Full; an unreferenced relation skips
// hydration entirely, and the persist pass restricts its delete scan to the
// hydrated keys so untouched rows survive.
object HydrationScope:

  enum KeySource derives CanEqual:
    // The value of an operation input (path, query, or body param).
    case Input(name: String)
    // The values of `elemField` across the `collectionField` elements of the
    // rows of `ownerRel` hydrated via `ownerKeys` (one dependent hop, e.g.
    // inventory keyed by the skus of an order's line items).
    case DependentField(
        ownerRel: String,
        ownerKeys: List[String],
        collectionField: String,
        elemField: String
    )

  enum Scope derives CanEqual:
    case Full
    case Keys(sources: List[KeySource])
    case Skip

  final case class OpScopes(byField: Map[String, Scope]):
    def apply(field: String): Scope = byField.getOrElse(field, Scope.Skip)
    def isFullEverywhere(relationFields: List[String]): Boolean =
      relationFields.forall(f => byField.get(f).contains(Scope.Full))

  def analyze(op: operation_decl, ir: ServiceIRFull): OpScopes =
    val stateDecls = irStateFields(ir)
    val seqNames   = stateDecls.filter(f => isSeqType(stfType(f))).map(stfName).toSet
    // Seq fields track alongside relations so any touch registers; the scope
    // assembly forces them Full regardless of the demand collected.
    val relationNames =
      stateDecls.filter(f => isRelationLike(stfType(f))).map(stfName).toSet ++ seqNames
    val inputs  = operInputs(op).map(prmName).toSet
    val outputs = operOutputs(op).map(prmName).toSet
    val clauses = flattenAndAll(operRequires(op)) ::: flattenAndAll(operEnsures(op))

    val demands = collection.mutable.Map.empty[String, Demand]
    def demand(rel: String, d: Demand): Unit =
      val next = demands.get(rel) match
        case Some(existing) => merge(existing, d)
        case None           => d
      demands.update(rel, next)

    // Let bindings whose value is a keyed read of a relation at input keys
    // stay key-transparent: `let user = user_by_email[email] in ... user.id`
    // demands only the email row. Bindings from anything else make later
    // keyed uses derived, which falls open to Full for that relation.
    def walk(e: expr, inputBound: Set[String]): Unit = e match
      case LetF(v, value, body, _) =>
        walk(value, inputBound)
        val transparent = value match
          case IndexF(rel, key, _)
              if peelName(rel).exists(relationNames) && inputKey(key, inputBound) =>
            true
          case _ => false
        walk(body, if transparent then inputBound + v else inputBound)
      case IndexF(relRef, key, _) if peelName(relRef).exists(relationNames) =>
        peelName(relRef).foreach { rel =>
          if inputKey(key, inputBound) then demand(rel, Demand.Keyed(keyName(key).toList))
          else demand(rel, Demand.Whole)
        }
        walk(key, inputBound)
      case BinaryOpF(BIn() | BNotIn(), key, relRef, _)
          if peelName(relRef).exists(relationNames) =>
        peelName(relRef).foreach { rel =>
          if inputKey(key, inputBound) then demand(rel, Demand.Keyed(keyName(key).toList))
          else demand(rel, Demand.Whole)
        }
        walk(key, inputBound)
      // Whole-relation assignment: the right-hand shape decides. Identity
      // frames demand nothing (the body leaves the relation alone), inserts
      // and deletes over the same relation are persist-only writes (the
      // upsert needs no pre-row, but persistence must still visit the
      // relation or a fresh-key insert would be dropped), and any other
      // rewrite falls open to Full.
      case BinaryOpF(BEq(), l, r, _) if peelName(l).exists(relationNames) =>
        peelName(l).foreach { rel =>
          r match
            case same if peelName(same).contains(rel) => ()
            case BinaryOpF(BAdd() | BSub(), base, delta, _)
                if peelName(base).contains(rel) =>
              demand(rel, Demand.Write)
              walk(delta, inputBound)
            case other =>
              demand(rel, Demand.Whole)
              walk(other, inputBound)
        }
      case BinaryOpF(_, l, r, _)                                                       => walk(l, inputBound); walk(r, inputBound)
      case UnaryOpF(UCardinality(), inner, _) if peelName(inner).exists(relationNames) =>
        // Cardinality claims are ensures-side and discharge statically.
        ()
      case UnaryOpF(_, inner, _) => walk(inner, inputBound)
      case QuantifierF(_, bs, body, _) =>
        bs.foreach { b =>
          val dom = qbdCollection(b)
          peelName(dom) match
            case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
            case _                               => walk(dom, inputBound)
        }
        walk(body, inputBound)
      case SetComprehensionF(_, dom, p, _) =>
        peelName(dom) match
          case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
          case _                               => walk(dom, inputBound)
        walk(p, inputBound)
      case TheF(_, dom, body, _) =>
        peelName(dom) match
          case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
          case _                               => walk(dom, inputBound)
        walk(body, inputBound)
      case CallF(c, args, _) =>
        peelName(c).filter(relationNames).foreach(rel => demand(rel, Demand.Whole))
        args.foreach { a =>
          peelName(a) match
            case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
            case _                               => walk(a, inputBound)
        }
      case IdentifierF(n, _) if relationNames(n) =>
        // A bare relation reference outside every recognized shape.
        demand(n, Demand.Whole)
      case IdentifierF(_, _) => ()
      case other =>
        childExprs(other).foreach(walk(_, inputBound))

    clauses.foreach(walk(_, inputs))

    // Seq state fields persist by delete-all-and-reinsert, so any touch at
    // all means full hydration.
    val scoped = demands.toMap.map {
      case (rel, _) if seqNames(rel) => rel -> Scope.Full
      case (rel, Demand.Whole)       => rel -> Scope.Full
      case (rel, Demand.Write)       => rel -> Scope.Keys(Nil)
      case (rel, Demand.Keyed(names)) =>
        rel -> Scope.Keys(names.distinct.map(KeySource.Input(_)))
    }
    val _ = outputs
    OpScopes(invariantClosure(scoped, ir, relationNames))

  // The runtime guard evaluates ServiceStateInv on the hydrated state, and
  // the kernel's proof assumes it, so the scope must be closed under the
  // invariants' cross-relation reach: a universal over hydrated rows whose
  // body reads another relation needs that relation whole (a subset of a
  // conforming relation re-satisfies the universal only when its references
  // resolve), a dom-equality clause needs the two relations hydrated at the
  // same keys, and any clause shape the classifier cannot vouch for forces
  // its relations whole on every operation. Per-row clauses (the domain
  // relation touched only through the binders) hold on any subset for free.
  private def invariantClosure(
      base: Map[String, Scope],
      ir: ServiceIRFull,
      relationNames: Set[String]
  ): Map[String, Scope] =
    val shapes = svcInvariants(ir).map(inv => classifyInvariant(invBody(inv), relationNames))
    def nonemptyPossible(s: Option[Scope]): Boolean = s match
      case Some(Scope.Full)     => true
      case Some(Scope.Keys(ks)) => ks.nonEmpty
      case _                    => false
    def step(m: Map[String, Scope]): Map[String, Scope] =
      shapes.foldLeft(m) { (acc, shape) =>
        shape match
          case InvShape.Safe => acc
          case InvShape.Cross(domains, refs) =>
            if domains.exists(d => nonemptyPossible(acc.get(d))) then
              refs.foldLeft(acc)((a, r) => a.updated(r, Scope.Full))
            else acc
          case InvShape.Aligned(x, y) => align(acc, x, y)
          case InvShape.Opaque(mentioned) =>
            mentioned.foldLeft(acc)((a, r) => a.updated(r, Scope.Full))
      }
    @annotation.tailrec
    def loop(m: Map[String, Scope]): Map[String, Scope] =
      val next = step(m)
      if next.sizeIs == m.size && next.forall((k, v) => m.get(k).contains(v)) then m
      else loop(next)
    loop(base)

  // dom-equality holds on hydrated slices exactly when both relations load
  // the same key set, so alignment raises the lesser side: a skipped or
  // persist-only relation adopts the other side's keys, and any other
  // mismatch loads both whole.
  private def align(m: Map[String, Scope], x: String, y: String): Map[String, Scope] =
    def emptyHydration(s: Option[Scope]): Boolean = s match
      case None                 => true
      case Some(Scope.Skip)     => true
      case Some(Scope.Keys(ks)) => ks.isEmpty
      case _                    => false
    (m.get(x), m.get(y)) match
      case (a, b) if a == b                                 => m
      case (a, b) if emptyHydration(a) && emptyHydration(b) => m
      case (Some(Scope.Keys(ks)), b) if ks.nonEmpty && emptyHydration(b) =>
        m.updated(y, Scope.Keys(ks))
      case (a, Some(Scope.Keys(ks))) if ks.nonEmpty && emptyHydration(a) =>
        m.updated(x, Scope.Keys(ks))
      case _ => m.updated(x, Scope.Full).updated(y, Scope.Full)

  private enum InvShape derives CanEqual:
    case Safe
    case Cross(domains: Set[String], refs: Set[String])
    case Aligned(x: String, y: String)
    case Opaque(mentioned: Set[String])

  private def classifyInvariant(body: expr, relationNames: Set[String]): InvShape =
    def mentioned(e: expr): Set[String] = e match
      case IdentifierF(n, _) => if relationNames(n) then Set(n) else Set.empty
      case QuantifierF(_, bs, b, _) =>
        bs.flatMap(x => mentioned(qbdCollection(x))).toSet ++ mentioned(b)
      case SetComprehensionF(_, d, p, _) => mentioned(d) ++ mentioned(p)
      case TheF(_, d, b, _)              => mentioned(d) ++ mentioned(b)
      case BinaryOpF(_, l, r, _)         => mentioned(l) ++ mentioned(r)
      case UnaryOpF(_, i, _)             => mentioned(i)
      case IndexF(t, k, _)               => mentioned(t) ++ mentioned(k)
      case CallF(c, as, _)               => (c :: as).flatMap(mentioned).toSet
      case LetF(_, v, b, _)              => mentioned(v) ++ mentioned(b)
      case other                         => childExprs(other).flatMap(mentioned).toSet
    val all = mentioned(body)
    if all.isEmpty then InvShape.Safe
    else
      body match
        case BinaryOpF(
              BEq(),
              CallF(IdentifierF("dom", _), a :: Nil, _),
              CallF(IdentifierF("dom", _), b :: Nil, _),
              _
            ) =>
          (peelName(a).filter(relationNames), peelName(b).filter(relationNames)) match
            case (Some(x), Some(y)) => InvShape.Aligned(x, y)
            case _                  => InvShape.Opaque(all)
        case QuantifierF(QAll(), bs, inner, _) =>
          classifyForall(bs, inner, relationNames).getOrElse(InvShape.Opaque(all))
        case _ => InvShape.Opaque(all)

  // A binding collection that is a relation, possibly through ran()/dom().
  private def bindingRel(col: expr, relationNames: Set[String]): Option[String] = col match
    case CallF(IdentifierF("ran" | "dom", _), arg :: Nil, _) =>
      peelName(arg).filter(relationNames)
    case other => peelName(other).filter(relationNames)

  private def classifyForall(
      bindings: List[quantifier_binding],
      inner: expr,
      relationNames: Set[String]
  ): Option[InvShape] =
    val domains = bindings.flatMap(b => bindingRel(qbdCollection(b), relationNames)).toSet
    if bindings.exists(b => bindingRel(qbdCollection(b), relationNames).isEmpty) then None
    else
      // None = the clause reaches the domain relation outside its binders,
      // so subset-safety cannot be vouched for; Some(refs) = the other
      // relations the body reads, which must load whole.
      def merge(acc: Option[Set[String]], next: Option[Set[String]]): Option[Set[String]] =
        for a <- acc; b <- next yield a ++ b
      def collect(e: expr, vars: Set[String]): Option[Set[String]] = e match
        case IndexF(rel, IdentifierF(v, _), _) if peelName(rel).exists(domains) =>
          if vars.contains(v) then Some(Set.empty) else None
        case IndexF(rel, _, _) if peelName(rel).exists(domains) => None
        case IndexF(rel, key, _) if peelName(rel).exists(relationNames) =>
          collect(key, vars).map(_ ++ peelName(rel).toSet)
        case BinaryOpF(BIn() | BNotIn(), l, relRef, _)
            if peelName(relRef).exists(relationNames) =>
          peelName(relRef) match
            case Some(r) if domains(r) => None
            case r                     => collect(l, vars).map(_ ++ r.toSet)
        case QuantifierF(_, nbs, nb, _) =>
          val fromBindings = nbs.foldLeft(Option(Set.empty[String])) { (acc, b) =>
            acc.flatMap { s =>
              val col = qbdCollection(b)
              bindingRel(col, relationNames) match
                case Some(r) => if domains(r) then None else Some(s + r)
                case None    => collect(col, vars).map(s ++ _)
            }
          }
          merge(fromBindings, collect(nb, vars ++ nbs.map(qbdVar)))
        case IdentifierF(n, _) if domains(n)       => None
        case IdentifierF(n, _) if relationNames(n) => Some(Set(n))
        case IdentifierF(_, _)                     => Some(Set.empty)
        case BinaryOpF(_, l, r, _)                 => merge(collect(l, vars), collect(r, vars))
        case UnaryOpF(_, i, _)                     => collect(i, vars)
        case CallF(c, as, _) =>
          (c :: as).foldLeft(Option(Set.empty[String]))((acc, a) => merge(acc, collect(a, vars)))
        case LetF(v, value, b, _) =>
          merge(collect(value, vars), collect(b, vars - v))
        case other =>
          childExprs(other).foldLeft(Option(Set.empty[String]))((acc, c) =>
            merge(acc, collect(c, vars))
          )
      collect(inner, bindings.map(qbdVar).toSet) match
        case None                   => None
        case Some(rs) if rs.isEmpty => Some(InvShape.Safe)
        case Some(rs)               => Some(InvShape.Cross(domains, rs))

  private enum Demand derives CanEqual:
    case Whole
    case Keyed(inputNames: List[String])
    // Persist-only: the body writes the relation (fresh-key insert) but no
    // clause reads existing rows.
    case Write

  private def merge(a: Demand, b: Demand): Demand = (a, b) match
    case (Demand.Keyed(x), Demand.Keyed(y)) => Demand.Keyed(x ::: y)
    case (Demand.Write, other)              => other
    case (other, Demand.Write)              => other
    case _                                  => Demand.Whole

  private def isRelationLike(t: type_expr): Boolean = t match
    case _: MapTypeF | _: RelationTypeF => true
    case _                              => false

  private def isSeqType(t: type_expr): Boolean = t match
    case _: SeqTypeF => true
    case _           => false

  // A relation reference possibly wrapped in pre()/prime.
  private def peelName(e: expr): Option[String] = e match
    case IdentifierF(n, _) => Some(n)
    case PreF(inner, _)    => peelName(inner)
    case PrimeF(inner, _)  => peelName(inner)
    case _                 => None

  private def inputKey(key: expr, inputBound: Set[String]): Boolean = key match
    case IdentifierF(n, _) => inputBound.contains(n)
    case _                 => false

  private def keyName(key: expr): Option[String] = key match
    case IdentifierF(n, _) => Some(n)
    case _                 => None

  private def childExprs(e: expr): List[expr] = e match
    case PreF(inner, _)          => List(inner)
    case PrimeF(inner, _)        => List(inner)
    case IfF(c, t, el, _)        => List(c, t, el)
    case FieldAccessF(b, _, _)   => List(b)
    case SomeWrapF(inner, _)     => List(inner)
    case MatchesF(inner, _, _)   => List(inner)
    case WithF(b, fas, _)        => b :: fas.map(fasValue)
    case ConstructorF(_, fas, _) => fas.map(fasValue)
    case LambdaF(_, b, _)        => List(b)
    case SetLiteralF(es, _)      => es
    case SeqLiteralF(es, _)      => es
    case MapLiteralF(entries, _) =>
      entries.flatMap(me => List(mpeKey(me), mpeValue(me)))
    case _ => Nil
