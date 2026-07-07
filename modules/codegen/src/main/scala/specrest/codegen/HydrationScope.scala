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
    // The target's keys are the values of `field` across the rows of
    // `sourceRel` hydrated in an earlier phase (users keyed by the id field
    // of the hydrated user_by_email rows).
    case FieldOfRows(sourceRel: String, field: String)
    // The target's keys are the values of `elemField` across the nested
    // entity rows referenced by `collectionField` of the hydrated
    // `sourceRel` rows (inventory keyed by the skus of the hydrated orders'
    // line items).
    case DependentField(sourceRel: String, collectionField: String, elemField: String)
    // Rows of the target whose `column` equals one of the hydrated keys of
    // `sourceRel` (payments whose order_id is a hydrated order key). Yields
    // no target keys ahead of the load, so the persist delete scan must key
    // off the row keys recorded at hydrate time.
    case ValueColumn(sourceRel: String, column: String)

  enum Scope derives CanEqual:
    case Full
    case Keys(sources: List[KeySource])
    case Skip

  final case class OpScopes(byField: Map[String, Scope]):
    def apply(field: String): Scope = byField.getOrElse(field, Scope.Skip)

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

    // Bindings that stay key-transparent through the walk: an operation
    // input, a row read at a transparent key (`let user = user_by_email[
    // email]`), a field of such a row (`let user_id = pre(reset_tokens)[
    // reset_token].user_id`), and a definite description over such a row's
    // collection (`let removed = the i in pre(orders)[order_id].items｜..`).
    // A key built from one of these resolves to an Input, FieldOfRows, or
    // DependentField source; anything else falls open to Full.
    def keySources(key: expr, env: Map[String, Binding]): Option[List[KeySource]] = key match
      case IdentifierF(v, _) =>
        env.get(v).collect {
          case Binding.InputVal         => List(KeySource.Input(v))
          case Binding.FieldVal(rel, f) => List(KeySource.FieldOfRows(rel, f))
        }
      case FieldAccessF(IdentifierF(v, _), f, _) =>
        env.get(v).collect {
          case Binding.RowOf(rel)        => List(KeySource.FieldOfRows(rel, f))
          case Binding.ElemOf(rel, coll) => List(KeySource.DependentField(rel, coll, f))
        }
      case _ => None
    def bindingOf(value: expr, env: Map[String, Binding]): Option[Binding] = value match
      case IndexF(rel, key, _)
          if peelName(rel).exists(relationNames) && keySources(key, env).isDefined =>
        peelName(rel).map(Binding.RowOf(_))
      case FieldAccessF(IndexF(rel, key, _), f, _)
          if peelName(rel).exists(relationNames) && keySources(key, env).isDefined =>
        peelName(rel).map(Binding.FieldVal(_, f))
      case TheF(_, FieldAccessF(IndexF(rel, key, _), coll, _), _, _)
          if peelName(rel).exists(relationNames) && keySources(key, env).isDefined =>
        peelName(rel).map(Binding.ElemOf(_, coll))
      case _ => None
    def walk(e: expr, env: Map[String, Binding]): Unit = e match
      case LetF(v, value, body, _) =>
        walk(value, env)
        walk(body, bindingOf(value, env).fold(env - v)(b => env.updated(v, b)))
      case IndexF(relRef, key, _) if peelName(relRef).exists(relationNames) =>
        peelName(relRef).foreach { rel =>
          keySources(key, env) match
            case Some(srcs) => demand(rel, Demand.Keyed(srcs))
            case None       => demand(rel, Demand.Whole)
        }
        walk(key, env)
      case BinaryOpF(BIn() | BNotIn(), key, relRef, _)
          if peelName(relRef).exists(relationNames) =>
        peelName(relRef).foreach { rel =>
          keySources(key, env) match
            case Some(srcs) => demand(rel, Demand.Keyed(srcs))
            case None       => demand(rel, Demand.Whole)
        }
        walk(key, env)
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
              walk(delta, env)
            case other =>
              demand(rel, Demand.Whole)
              walk(other, env)
        }
      case BinaryOpF(_, l, r, _)                                                       => walk(l, env); walk(r, env)
      case UnaryOpF(UCardinality(), inner, _) if peelName(inner).exists(relationNames) =>
        // Cardinality claims are ensures-side and discharge statically.
        ()
      case UnaryOpF(_, inner, _) => walk(inner, env)
      case QuantifierF(_, bs, body, _) =>
        bs.foreach { b =>
          val dom = qbdCollection(b)
          peelName(dom) match
            case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
            case _                               => walk(dom, env)
        }
        walk(body, env)
      case SetComprehensionF(_, dom, p, _) =>
        peelName(dom) match
          case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
          case _                               => walk(dom, env)
        walk(p, env)
      case TheF(_, dom, body, _) =>
        peelName(dom) match
          case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
          case _                               => walk(dom, env)
        walk(body, env)
      case CallF(c, args, _) =>
        peelName(c).filter(relationNames).foreach(rel => demand(rel, Demand.Whole))
        args.foreach { a =>
          peelName(a) match
            case Some(rel) if relationNames(rel) => demand(rel, Demand.Whole)
            case _                               => walk(a, env)
        }
      case IdentifierF(n, _) if relationNames(n) =>
        // A bare relation reference outside every recognized shape.
        demand(n, Demand.Whole)
      case IdentifierF(_, _) => ()
      case other =>
        childExprs(other).foreach(walk(_, env))

    clauses.foreach(walk(_, inputs.map(_ -> Binding.InputVal).toMap))

    // Seq state fields persist by delete-all-and-reinsert, so any touch at
    // all means full hydration.
    val scoped = demands.toMap.map {
      case (rel, _) if seqNames(rel) => rel -> Scope.Full
      case (rel, Demand.Whole)       => rel -> Scope.Full
      case (rel, Demand.Write)       => rel -> Scope.Keys(Nil)
      case (rel, Demand.Keyed(sources)) =>
        rel -> Scope.Keys(sources.distinct)
    }
    val _ = outputs
    OpScopes(invariantClosure(scoped, ir, relationNames, seqNames))

  // The runtime guard evaluates ServiceStateInv on the hydrated state, and
  // the kernel's proof assumes it, so the scope must be closed under the
  // invariants' cross-relation reach. Per-row clauses (the domain relation
  // touched only through the binders) hold on any subset for free. A
  // cross-relation reference hydrates the referenced relation's support:
  // rows keyed by an owner field resolve through FieldOfRows/DependentField
  // loads, a column-guarded existential resolves through a ValueColumn
  // filter, and a guarded universal over another relation shrinks safely
  // and needs nothing. dom-equality clauses align the two relations' key
  // sets. Anything the classifier cannot vouch for loads whole, and derived
  // cycles without a certificate fall back to whole relations: the
  // ecommerce orders/payments cycle is cut structurally (the ValueColumn
  // filter confines every loaded row's reference to the hydrated source
  // keys), and the auth users/user_by_email cycle is cut by the bilateral
  // index-equality conjuncts that pin each newly loaded row to an already
  // hydrated one.
  private def invariantClosure(
      base: Map[String, Scope],
      ir: ServiceIRFull,
      relationNames: Set[String],
      seqNames: Set[String]
  ): Map[String, Scope] =
    val shapes = svcInvariants(ir).map(inv => classifyInvariant(invBody(inv), relationNames))
    def canHydrate(s: Option[Scope]): Boolean = s match
      case Some(Scope.Full)     => true
      case Some(Scope.Keys(ks)) => ks.nonEmpty
      case _                    => false
    def rowSources(s: Option[Scope]): List[KeySource] = s match
      case Some(Scope.Keys(ks)) => ks
      case _                    => Nil
    def raise(m: Map[String, Scope], rel: String): Map[String, Scope] =
      m.updated(rel, Scope.Full)
    def addSource(m: Map[String, Scope], rel: String, src: KeySource): Map[String, Scope] =
      if seqNames(rel) then raise(m, rel)
      else
        m.get(rel) match
          case Some(Scope.Full) => m
          case Some(Scope.Keys(existing)) =>
            if existing.contains(src) then m else m.updated(rel, Scope.Keys(existing :+ src))
          case _ => m.updated(rel, Scope.Keys(List(src)))
    // A clause over the target relation that sends `column` of every row
    // into the source relation: a ValueColumn load from a fully hydrated
    // source then selects the whole target, so load it whole outright.
    val coversVia: Set[(String, String, String)] = shapes.collect {
      case InvShape.Cross(ds, edges, _, _) =>
        edges.collect { case Edge(target, EdgeKind.FieldKey(col)) =>
          ds.toList.map(d => (d, target, col))
        }.flatten
    }.flatten.toSet

    def applyCross(
        m: Map[String, Scope],
        domains: Set[String],
        edges: List[Edge]
    ): Map[String, Scope] =
      val live = domains.filter(d => canHydrate(m.get(d)))
      if live.isEmpty then m
      else
        edges.foldLeft(m) { (acc, edge) =>
          edge.kind match
            case EdgeKind.WholeRelation => raise(acc, edge.target)
            case EdgeKind.FieldKey(f) =>
              live.foldLeft(acc) { (a, d) =>
                // Structural cut: a domain hydrated solely through a
                // ValueColumn filter on this very column already confines
                // every row's reference to the target's hydrated keys.
                val srcs = rowSources(a.get(d))
                val cut = srcs.nonEmpty && srcs.forall {
                  case KeySource.ValueColumn(s, c) => s == edge.target && c == f
                  case _                           => false
                }
                if cut then a else addSource(a, edge.target, KeySource.FieldOfRows(d, f))
              }
            case EdgeKind.NestedFieldKey(coll, ef) =>
              live.foldLeft(acc)((a, d) =>
                addSource(a, edge.target, KeySource.DependentField(d, coll, ef))
              )
            case EdgeKind.ColumnFilter(col) =>
              live.foldLeft(acc) { (a, d) =>
                if a.get(d).contains(Scope.Full) && coversVia((edge.target, d, col)) then
                  raise(a, edge.target)
                else addSource(a, edge.target, KeySource.ValueColumn(d, col))
              }
        }

    def step(m: Map[String, Scope]): Map[String, Scope] =
      shapes.foldLeft(m) { (acc, shape) =>
        shape match
          case InvShape.Safe                        => acc
          case InvShape.Cross(domains, edges, _, _) => applyCross(acc, domains, edges)
          case InvShape.Aligned(x, y)               => align(acc, x, y)
          case InvShape.Opaque(mentioned) =>
            mentioned.foldLeft(acc)((a, r) => raise(a, r))
      }
    @annotation.tailrec
    def loop(m: Map[String, Scope]): Map[String, Scope] =
      val next = step(m)
      if next.sizeIs == m.size && next.forall((k, v) => m.get(k).contains(v)) then m
      else loop(next)
    certifyCycles(loop(base), shapes)

  // Derived loads that feed each other form phased chains; a cycle among
  // them is sound only when it provably terminates. A two-relation cycle is
  // kept when each side's generating clause pins the referenced row back to
  // an already hydrated one (an index-equality conjunct) and agrees on its
  // own key (users[u].id = u), which makes the third phase re-load only
  // hydrated keys. Every other cycle falls back to whole relations.
  private def certifyCycles(
      m: Map[String, Scope],
      shapes: List[InvShape]
  ): Map[String, Scope] =
    def derivedDeps(s: Scope): Set[String] = s match
      case Scope.Keys(ks) =>
        ks.collect {
          case KeySource.FieldOfRows(src, _)       => src
          case KeySource.DependentField(src, _, _) => src
          case KeySource.ValueColumn(src, _)       => src
        }.toSet
      case _ => Set.empty
    val deps = m.map((rel, s) => rel -> derivedDeps(s))
    def reaches(from: String, to: String, seen: Set[String]): Boolean =
      deps.getOrElse(from, Set.empty).exists { n =>
        n == to || (!seen(n) && reaches(n, to, seen + n))
      }
    def pinnedBoth(a: String, b: String): Boolean =
      def pinned(domain: String, target: String): Boolean =
        shapes.exists {
          case InvShape.Cross(ds, _, pins, keyAgree) =>
            ds.contains(domain) && pins.contains(target) && keyAgree
          case _ => false
        }
      pinned(a, b) && pinned(b, a)
    // A dependency edge participates in a cycle when its target can reach
    // back. Every such edge must be one half of a certified mutual pair; a
    // self-loop or a longer cycle has no certificate and loads whole.
    val toFull = m.keySet.filter { r =>
      deps.getOrElse(r, Set.empty).exists { d =>
        val backReaches = d == r || reaches(d, r, Set.empty)
        backReaches &&
        (d == r || !(deps.getOrElse(d, Set.empty).contains(r) && pinnedBoth(r, d)))
      }
    }
    toFull.foldLeft(m)((acc, r) => acc.updated(r, Scope.Full))

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

  final private case class Edge(target: String, kind: EdgeKind)

  private enum EdgeKind derives CanEqual:
    // Reference keyed by a field of the hydrated domain row.
    case FieldKey(field: String)
    // Reference keyed by a field of the domain row's nested entity elements.
    case NestedFieldKey(collection: String, elemField: String)
    // Column-guarded existential: rows whose column equals the domain key.
    case ColumnFilter(column: String)
    // A reference shape with no finite support: load the target whole.
    case WholeRelation

  private enum InvShape derives CanEqual:
    case Safe
    case Cross(
        domains: Set[String],
        edges: List[Edge],
        pinnedTargets: Set[String],
        keyAgreement: Boolean
    )
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
      val keyVars = bindings.map(qbdVar).toSet
      // The domain row and its field paths, the only sanctioned ways the
      // clause body may touch the domain relation.
      def isOwnerRow(e: expr): Boolean = e match
        case IndexF(rel, IdentifierF(v, _), _) =>
          peelName(rel).exists(domains) && keyVars.contains(v)
        case _ => false
      // env: nested binders over a relation (var -> relation) and nested
      // binders over an owner field collection (var -> collection field).
      final case class Env(relVars: Map[String, String], elemVars: Map[String, String])
      def fieldPath(e: expr, env: Env): Option[EdgeKind] = e match
        case FieldAccessF(row, f, _) if isOwnerRow(row) => Some(EdgeKind.FieldKey(f))
        case FieldAccessF(IdentifierF(v, _), f, _) if env.elemVars.contains(v) =>
          env.elemVars.get(v).map(coll => EdgeKind.NestedFieldKey(coll, f))
        case _ => None
      def merge(a: Option[List[Edge]], b: Option[List[Edge]]): Option[List[Edge]] =
        for x <- a; y <- b yield x ::: y
      // The column-equality conjunct that ties an existential witness to the
      // domain key: B[pid].col = k with pid the witness binder, k a key var.
      def guardColumn(conjuncts: List[expr], rel: String, binder: String): Option[String] =
        conjuncts.collectFirst {
          case BinaryOpF(
                BEq(),
                FieldAccessF(IndexF(r, IdentifierF(p, _), _), col, _),
                IdentifierF(k, _),
                _
              )
              if peelName(r).contains(rel) && p == binder && keyVars.contains(k) =>
            col
          case BinaryOpF(
                BEq(),
                IdentifierF(k, _),
                FieldAccessF(IndexF(r, IdentifierF(p, _), _), col, _),
                _
              )
              if peelName(r).contains(rel) && p == binder && keyVars.contains(k) =>
            col
        }
      def collect(e: expr, env: Env): Option[List[Edge]] = e match
        case row if isOwnerRow(row)                             => Some(Nil)
        case IndexF(rel, _, _) if peelName(rel).exists(domains) => None
        case IndexF(rel, key, _) if peelName(rel).exists(relationNames) =>
          val target = peelName(rel).getOrElse("")
          key match
            case IdentifierF(v, _) if env.relVars.get(v).contains(target) => Some(Nil)
            case _ =>
              fieldPath(key, env) match
                case Some(kind) => collect(key, env).map(Edge(target, kind) :: _)
                case None =>
                  collect(key, env).map(Edge(target, EdgeKind.WholeRelation) :: _)
        case BinaryOpF(BIn() | BNotIn(), l, relRef, _)
            if peelName(relRef).exists(relationNames) =>
          peelName(relRef) match
            case Some(r) if domains(r) => None
            case Some(r) =>
              fieldPath(l, env) match
                case Some(kind) => collect(l, env).map(Edge(r, kind) :: _)
                case None       => collect(l, env).map(Edge(r, EdgeKind.WholeRelation) :: _)
            case None => collect(l, env)
        case QuantifierF(kind, nbs, nb, _) =>
          val existential = kind match
            case QSome() | QExists() => true
            case _                   => false
          val bound = nbs.foldLeft(Option((env, List.empty[Edge]))) { (acc, b) =>
            acc.flatMap { (envAcc, edges) =>
              val col = qbdCollection(b)
              bindingRel(col, relationNames) match
                case Some(r) if domains(r) => None
                case Some(r) =>
                  val nextEnv = envAcc.copy(relVars = envAcc.relVars.updated(qbdVar(b), r))
                  if existential then
                    guardColumn(flattenAndAll(List(nb)), r, qbdVar(b)) match
                      case Some(c) => Some((nextEnv, Edge(r, EdgeKind.ColumnFilter(c)) :: edges))
                      case None    => Some((nextEnv, Edge(r, EdgeKind.WholeRelation) :: edges))
                  else Some((nextEnv, edges))
                case None =>
                  val elem = col match
                    case FieldAccessF(row, f, _) if isOwnerRow(row) => Some(f)
                    case _                                          => None
                  elem match
                    case Some(f) =>
                      Some((envAcc.copy(elemVars = envAcc.elemVars.updated(qbdVar(b), f)), edges))
                    case None => collect(col, envAcc).map(es => (envAcc, es ::: edges))
            }
          }
          bound.flatMap((envB, edges) => collect(nb, envB).map(edges ::: _))
        case IdentifierF(n, _) if domains(n)       => None
        case IdentifierF(n, _) if relationNames(n) => Some(List(Edge(n, EdgeKind.WholeRelation)))
        case IdentifierF(_, _)                     => Some(Nil)
        case BinaryOpF(_, l, r, _)                 => merge(collect(l, env), collect(r, env))
        case UnaryOpF(_, i, _)                     => collect(i, env)
        case CallF(c, as, _) =>
          (c :: as).foldLeft(Option(List.empty[Edge]))((acc, a) => merge(acc, collect(a, env)))
        case LetF(v, value, b, _) =>
          merge(
            collect(value, env),
            collect(b, env.copy(relVars = env.relVars - v, elemVars = env.elemVars - v))
          )
        case other =>
          childExprs(other).foldLeft(Option(List.empty[Edge]))((acc, c) =>
            merge(acc, collect(c, env))
          )
      collect(inner, Env(Map.empty, Map.empty)) match
        case None => None
        case Some(edges) =>
          if edges.isEmpty then Some(InvShape.Safe)
          else
            val conjuncts = flattenAndAll(List(inner))
            // A conjunct that equates the referenced row with the domain row
            // (users[ube[email].id] = ube[email]) pins the phased load: under
            // conformance the next phase re-keys only hydrated rows.
            val pinned = conjuncts.collect {
              case BinaryOpF(BEq(), IndexF(rel, key, _), rhs, _)
                  if peelName(rel).exists(relationNames)
                    && fieldPath(key, Env(Map.empty, Map.empty)).isDefined
                    && isOwnerRow(rhs) =>
                peelName(rel).toList
              case BinaryOpF(BEq(), lhs, IndexF(rel, key, _), _)
                  if peelName(rel).exists(relationNames)
                    && fieldPath(key, Env(Map.empty, Map.empty)).isDefined
                    && isOwnerRow(lhs) =>
                peelName(rel).toList
            }.flatten.toSet
            // A conjunct that ties the domain row's field to its own key
            // (users[u].id = u), the other half of the cycle certificate.
            val keyAgreement = conjuncts.exists {
              case BinaryOpF(BEq(), FieldAccessF(row, _, _), IdentifierF(k, _), _) =>
                isOwnerRow(row) && keyVars.contains(k)
              case BinaryOpF(BEq(), IdentifierF(k, _), FieldAccessF(row, _, _), _) =>
                isOwnerRow(row) && keyVars.contains(k)
              case _ => false
            }
            Some(InvShape.Cross(domains, edges.distinct, pinned, keyAgreement))

  private enum Binding derives CanEqual:
    case InputVal
    case RowOf(rel: String)
    case FieldVal(rel: String, field: String)
    case ElemOf(rel: String, collectionField: String)

  private enum Demand derives CanEqual:
    case Whole
    case Keyed(sources: List[KeySource])
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
