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
    val stateDecls    = irStateFields(ir)
    val relationNames = stateDecls.filter(f => isRelationLike(stfType(f))).map(stfName).toSet
    val seqNames      = stateDecls.filter(f => isSeqType(stfType(f))).map(stfName).toSet
    val inputs        = operInputs(op).map(prmName).toSet
    val outputs       = operOutputs(op).map(prmName).toSet
    val clauses       = flattenAndAll(operRequires(op)) ::: flattenAndAll(operEnsures(op))

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
      // rel' = rel and rel' = pre(rel): the body leaves it alone; no rows.
      case BinaryOpF(BEq(), l, r, _)
          if peelName(l).exists(relationNames) && peelName(r) == peelName(l) =>
        ()
      // rel' = pre(rel) + {k -> v} / - {k}: the write itself needs no
      // pre-row (upsert/delete against hydrated keys), but persistence must
      // still visit the relation even when nothing else hydrates it, or a
      // fresh-key insert would be dropped. The key and value sub-expressions
      // walk normally so their own reads register.
      case BinaryOpF(BAdd() | BSub(), l, r, _) if peelName(l).exists(relationNames) =>
        peelName(l).foreach(rel => demand(rel, Demand.Write))
        walk(r, inputBound)
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
    OpScopes(scoped)

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
    case IfF(c, t, el, _)        => List(c, t, el)
    case FieldAccessF(b, _, _)   => List(b)
    case SomeWrapF(inner, _)     => List(inner)
    case MatchesF(inner, _, _)   => List(inner)
    case WithF(b, fas, _)        => b :: fas.map(fasValue)
    case ConstructorF(_, fas, _) => fas.map(fasValue)
    case SetLiteralF(es, _)      => es
    case SeqLiteralF(es, _)      => es
    case MapLiteralF(entries, _) =>
      entries.flatMap(me => List(mpeKey(me), mpeValue(me)))
    case _ => Nil
