package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated.*

import scala.collection.mutable

@SuppressWarnings(Array(
  "org.wartremover.warts.Var",
  "org.wartremover.warts.Return",
  "org.wartremover.warts.OptionPartial"
))
private[z3] trait SmtTermBridge:
  this: Z3EncodingSupport =>
  private[z3] def coerceOptionalNumeric(ctx: TranslateCtx, z: Z3Expr): Z3Expr =
    inferSortOfZ3Expr(ctx, z) match
      case Some(Z3Sort.OptionOf(e)) if Z3Sort.isNumeric(e) => Z3Expr.OptGet(z)
      case _                                               => z

  // Arithmetic/ordering are valid only on the verifier's numeric theory sorts
  // (Int for integers and temporal-as-epoch, Real for Float/Decimal/Money). An
  // operand of any other sort is outside the encodable subset, so the check is
  // skipped rather than handed to the solver as an ill-sorted term.
  private[z3] def encNumeric(
      ctx: TranslateCtx,
      term: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val z = coerceOptionalNumeric(ctx, encodeFromSmtTerm(ctx, term, env))
    if !inferSortOfZ3Expr(ctx, z).exists(Z3Sort.isNumeric) then
      fail(ctx, "ordering/arithmetic requires a numeric type (Int or Real)")
    z

  // Ordering: both operands numeric (Cmp, arithmetic <) or both String (StrCmp,
  // str.</str.<=). A numeric/String mix is left to the skip path rather than
  // handed to the solver ill-sorted.
  private[z3] def encCmp(
      ctx: TranslateCtx,
      op: CmpOp,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val lz = encodeFromSmtTerm(ctx, l, env)
    val rz = encodeFromSmtTerm(ctx, r, env)
    def isStr(z: Z3Expr): Boolean =
      inferSortOfZ3Expr(ctx, z) match
        case Some(Z3Sort.Str) => true
        case _                => false
    def isNum(z: Z3Expr): Boolean = inferSortOfZ3Expr(ctx, z).exists(Z3Sort.isNumeric)
    if isStr(lz) && isStr(rz) then Z3Expr.StrCmp(op, lz, rz)
    else if isNum(lz) && isNum(rz) then Z3Expr.Cmp(op, lz, rz)
    else fail(ctx, "ordering requires two numeric (Int or Real) operands or two String operands")

  // Equality/inequality of a verified TEq: coerce a base-vs-optional pair (e.g. `todo.title = title`
  // where title is Option[String]) by lifting the base to some(base), so the solver sees
  // Option[T] = Option[T] instead of an incompatible-sorts clash.
  private[z3] def encEq(
      ctx: TranslateCtx,
      op: CmpOp,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val (lz, rz) =
      coerceOptionalEquality(ctx, encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
    Z3Expr.Cmp(op, lz, rz)

  // Addition: both operands numeric (Arith Add) or both String (StrConcat, str.++);
  // a numeric/String mix is left to the skip path rather than handed to the solver
  // ill-sorted.
  private[z3] def encAdd(
      ctx: TranslateCtx,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val lz = coerceOptionalNumeric(ctx, encodeFromSmtTerm(ctx, l, env))
    val rz = coerceOptionalNumeric(ctx, encodeFromSmtTerm(ctx, r, env))
    def isStr(z: Z3Expr): Boolean =
      inferSortOfZ3Expr(ctx, z) match
        case Some(Z3Sort.Str) => true
        case _                => false
    def isNum(z: Z3Expr): Boolean = inferSortOfZ3Expr(ctx, z).exists(Z3Sort.isNumeric)
    def isSeq(z: Z3Expr): Boolean =
      inferSortOfZ3Expr(ctx, z) match
        case Some(Z3Sort.SeqOf(_)) => true
        case _                     => false
    // `set + set` is set union (e.g. `items + {item}`); both operands must share an element sort.
    def sameSet(a: Z3Expr, b: Z3Expr): Boolean =
      (inferSortOfZ3Expr(ctx, a), inferSortOfZ3Expr(ctx, b)) match
        case (Some(Z3Sort.SetOf(ae)), Some(Z3Sort.SetOf(be))) => Z3Sort.eq(ae, be)
        case _                                                => false
    if isStr(lz) && isStr(rz) then Z3Expr.StrConcat(lz, rz)
    else if isSeq(lz) && isSeq(rz) then Z3Expr.SeqConcat(lz, rz)
    else if isNum(lz) && isNum(rz) then Z3Expr.Arith(ArithOp.Add, List(lz, rz))
    else if sameSet(lz, rz) then Z3Expr.SetBinOp(SetOpKind.Union, lz, rz)
    else
      fail(
        ctx,
        "addition requires two numeric (Int or Real), two String, two Seq, or two Set operands"
      )

  private[z3] def encSub(
      ctx: TranslateCtx,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val lz                        = coerceOptionalNumeric(ctx, encodeFromSmtTerm(ctx, l, env))
    val rz                        = coerceOptionalNumeric(ctx, encodeFromSmtTerm(ctx, r, env))
    def isNum(z: Z3Expr): Boolean = inferSortOfZ3Expr(ctx, z).exists(Z3Sort.isNumeric)
    // `set - set` is set difference (e.g. `items - {removed}`); both operands must share an element sort.
    def sameSet(a: Z3Expr, b: Z3Expr): Boolean =
      (inferSortOfZ3Expr(ctx, a), inferSortOfZ3Expr(ctx, b)) match
        case (Some(Z3Sort.SetOf(ae)), Some(Z3Sort.SetOf(be))) => Z3Sort.eq(ae, be)
        case _                                                => false
    if isNum(lz) && isNum(rz) then Z3Expr.Arith(ArithOp.Sub, List(lz, rz))
    else if sameSet(lz, rz) then Z3Expr.SetBinOp(SetOpKind.Diff, lz, rz)
    else
      fail(ctx, "subtraction requires two numeric (Int or Real) or two Set operands")

  private[z3] def encodeSetEmptyCmp(
      ctx: TranslateCtx,
      op: CmpOp,
      setTerm: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val sz = encodeFromSmtTerm(ctx, setTerm, env)
    inferSortOfZ3Expr(ctx, sz) match
      case Some(Z3Sort.SetOf(elemSort)) =>
        Z3Expr.Cmp(op, sz, Z3Expr.EmptySet(elemSort))
      case _ =>
        fail(ctx, "equality with an empty set literal requires a set-sorted operand")

  // Domain membership `elem in rel`, where `relMode` is the relation's state mode
  // (Post/Pre for `rel'`/`pre(rel)`). The element is encoded in the ambient
  // `ctx.stateMode`; only the relation/const lookup takes `relMode`.
  private[z3] def encInDom(
      ctx: TranslateCtx,
      rel: String,
      elem: smt_term,
      relMode: StateMode,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val key = encodeFromSmtTerm(ctx, elem, env)
    ctx.state.get(rel) match
      case Some(r: StateRelationInfo) =>
        Z3Expr.App(domFuncFor(r, relMode), List(key))
      case Some(c: StateConstInfo) =>
        val rhs = Z3Expr.App(constFuncFor(c, relMode), Nil)
        inferSortOfZ3Expr(ctx, rhs) match
          case Some(Z3Sort.SetOf(_)) => Z3Expr.SetMember(key, rhs)
          case _                     => fail(ctx, s"membership '$rel' requires a relation or set-typed constant")
      case _ if ctx.entities.contains(rel) =>
        val entitySort = ctx.entities(rel).sort
        inferSortOfZ3Expr(ctx, key) match
          case Some(s) if Z3Sort.eq(s, entitySort) => Z3Expr.BoolLit(true)
          case Some(s) =>
            fail(
              ctx,
              s"membership '$rel' requires an element of the entity sort $entitySort; got $s"
            )
          case None =>
            fail(ctx, s"membership '$rel' requires an element with an inferrable sort")
      case _ =>
        env.get(rel) match
          case Some(bound) =>
            inferSortOfZ3Expr(ctx, bound) match
              case Some(Z3Sort.SetOf(_)) => Z3Expr.SetMember(key, bound)
              case _                     => fail(ctx, s"membership '$rel' requires a set-typed binding")
          case None =>
            fail(ctx, s"membership '$rel' requires a state relation or set-typed value")

  private[z3] def encodeFromSmtTerm(
      ctx: TranslateCtx,
      term: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    term match
      case TEq(l, TNone())           => encodeNoneEq(ctx, l, env)
      case TEq(TNone(), r)           => encodeNoneEq(ctx, r, env)
      case TNot(TEq(l, TNone()))     => Z3Expr.Not(encodeNoneEq(ctx, l, env))
      case TNot(TEq(TNone(), r))     => Z3Expr.Not(encodeNoneEq(ctx, r, env))
      case TEq(l, TSetEmpty())       => encodeSetEmptyCmp(ctx, CmpOp.Eq, l, env)
      case TEq(TSetEmpty(), r)       => encodeSetEmptyCmp(ctx, CmpOp.Eq, r, env)
      case TNot(TEq(l, TSetEmpty())) => encodeSetEmptyCmp(ctx, CmpOp.Neq, l, env)
      case TNot(TEq(TSetEmpty(), r)) => encodeSetEmptyCmp(ctx, CmpOp.Neq, r, env)
      case BLit(b)                   => Z3Expr.BoolLit(b)
      case ILit(n)                   => Z3Expr.IntLit(n)
      case RLit(r) =>
        val (num, den) = quotient_of(r)
        Z3Expr.RealLit(num, den)
      case TVar(name) => resolveIdentifier(ctx, name, env)
      case EnumElemConst(en, mem) =>
        val funcName = s"${en}_$mem"
        if !ctx.funcs.contains(funcName) then
          val resultSort = ctx.enums.get(en).map(_.sort).getOrElse(Z3Sort.Uninterp(en))
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, resultSort))
        Z3Expr.App(funcName, Nil)

      case TNot(TEq(l, r)) => encEq(ctx, CmpOp.Neq, l, r, env)
      case TOr(TLt(a1, b1), TEq(a2, b2)) if a1 == a2 && b1 == b2 =>
        encCmp(ctx, CmpOp.Le, a1, b1, env)
      case TOr(TLt(b1, a1), TEq(a2, b2)) if a1 == a2 && b1 == b2 =>
        encCmp(ctx, CmpOp.Ge, a2, b2, env)

      case TNot(t) => Z3Expr.Not(encodeFromSmtTerm(ctx, t, env))
      case TAnd(l, r) =>
        Z3Expr.And(List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env)))
      case TOr(l, r) =>
        Z3Expr.Or(List(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env)))
      case TImplies(l, r) =>
        Z3Expr.Implies(encodeFromSmtTerm(ctx, l, env), encodeFromSmtTerm(ctx, r, env))
      case TEq(l, r) => encEq(ctx, CmpOp.Eq, l, r, env)
      case TLt(l, r) =>
        encCmp(ctx, CmpOp.Lt, l, r, env)
      case TNeg(t) =>
        Z3Expr.Arith(ArithOp.Sub, List(Z3Expr.IntLit(BigInt(0)), encNumeric(ctx, t, env)))
      case TAdd(l, r) =>
        encAdd(ctx, l, r, env)
      case TSub(l, r) =>
        encSub(ctx, l, r, env)
      case TMul(l, r) =>
        Z3Expr.Arith(ArithOp.Mul, List(encNumeric(ctx, l, env), encNumeric(ctx, r, env)))
      case TDiv(l, r) =>
        Z3Expr.Arith(ArithOp.Div, List(encNumeric(ctx, l, env), encNumeric(ctx, r, env)))

      case TInDom(rel, elem) =>
        encInDom(ctx, rel, elem, ctx.stateMode, env)
      // `#x`: a state relation gets its cardinality constant; an `x` bound in `env`
      // (an entity-field `Set`, e.g. `#items` inside an Order invariant, or a local
      // set binder) is a set-valued operand and gets the uninterpreted setCard model.
      case TCardRel(rel) =>
        env.get(rel) match
          case Some(setExpr) => entitySetCardinality(ctx, setExpr)
          case None          => cardinalityRefFor(ctx, rel, ctx.stateMode)
      // `#<set-valued expr>` (e.g. `#(orders[oid].items)`): encode the operand and apply the same
      // uninterpreted setCard model as an entity-field set. Non-set operands fail in setCard.
      case TCard(t) =>
        entitySetCardinality(ctx, encodeFromSmtTerm(ctx, t, env))

      case TLetIn(name, value, body) =>
        val v      = encodeFromSmtTerm(ctx, value, env)
        val newEnv = env.clone()
        newEnv(name) = v
        encodeFromSmtTerm(ctx, body, newEnv)

      case TForallEnum(varName, en, body) =>
        val sort   = ctx.enums.get(en).map(_.sort).getOrElse(Z3Sort.Uninterp(en))
        val newEnv = env.clone()
        newEnv(varName) = Z3Expr.Var(varName, sort)
        Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(varName, sort)),
          encodeFromSmtTerm(ctx, body, newEnv)
        )
      case TForallRel(varName, rel, body) =>
        // `translate` emits TForallRel for any identifier domain. When the domain is actually a
        // Seq-sorted value (e.g. an operation output Seq[T]) rather than a state relation, quantify
        // over the sequence's elements. eval is vacuous on a non-relation domain, so the seq
        // interpretation is trusted frame synthesis (like the #428 range projection).
        env.get(rel).flatMap(z => inferSortOfZ3Expr(ctx, z)) match
          case Some(Z3Sort.SeqOf(elem)) =>
            val seqZ = env(rel)
            // fresh binder so an outer identifier of the same name inside `seqZ` is not captured
            val freshName = ctx.freshSkolem(s"forall_seq_$varName")
            val binderVar = Z3Expr.Var(freshName, elem)
            val newEnv    = env.clone()
            newEnv(varName) = binderVar
            val inner = encodeFromSmtTerm(ctx, body, newEnv)
            Z3Expr.Quantifier(
              QKind.ForAll,
              List(Z3Binding(freshName, elem)),
              Z3Expr.Implies(Z3Expr.SeqContains(seqZ, binderVar), inner)
            )
          case _ =>
            val (sort, guard) = quantifierDomainFor(ctx, rel, varName)
            val newEnv        = env.clone()
            newEnv(varName) = Z3Expr.Var(varName, sort)
            val inner = encodeFromSmtTerm(ctx, body, newEnv)
            val guarded = guard match
              case Some(g) => Z3Expr.Implies(g, inner)
              case None    => inner
            Z3Expr.Quantifier(
              QKind.ForAll,
              List(Z3Binding(varName, sort)),
              guarded
            )

      case TExistsRel(varName, rel, body) =>
        val (sort, guard) = quantifierDomainFor(ctx, rel, varName)
        val newEnv        = env.clone()
        newEnv(varName) = Z3Expr.Var(varName, sort)
        val inner = encodeFromSmtTerm(ctx, body, newEnv)
        val guarded = guard match
          case Some(g) => Z3Expr.And(List(g, inner))
          case None    => inner
        Z3Expr.Quantifier(
          QKind.Exists,
          List(Z3Binding(varName, sort)),
          guarded
        )

      case TForallSet(varName, setT, body) =>
        val setZ = encodeFromSmtTerm(ctx, setT, env)
        val elemSort = inferSortOfZ3Expr(ctx, setZ) match
          case Some(Z3Sort.SetOf(e)) => e
          case _ =>
            fail(ctx, "universal quantification requires a set-sorted domain")
        // fresh binder so an outer identifier of the same name inside `setZ` is not captured
        val freshName = ctx.freshSkolem(s"forall_set_$varName")
        val binderVar = Z3Expr.Var(freshName, elemSort)
        val newEnv    = env.clone()
        newEnv(varName) = binderVar
        val inner = encodeFromSmtTerm(ctx, body, newEnv)
        Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(freshName, elemSort)),
          Z3Expr.Implies(Z3Expr.SetMember(binderVar, setZ), inner)
        )

      case TTheSet(varName, setT, body) =>
        val setZ = encodeFromSmtTerm(ctx, setT, env)
        val elemSort = inferSortOfZ3Expr(ctx, setZ) match
          case Some(Z3Sort.SetOf(e)) => e
          case _ =>
            fail(ctx, "definite description `the` requires a set-sorted domain")
        // a Skolem constant denotes "the unique element" only at quantifier-free position;
        // under a binder it would need a Skolem function of the bound vars (mirrors TTheRel)
        if env.values.exists { case _: Z3Expr.Var => true; case _ => false } then
          fail(ctx, "definite description `the` is not supported under a quantifier")
        val skolemName = ctx.freshSkolem(s"the_set_$varName")
        ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, elemSort))
        val c    = Z3Expr.App(skolemName, Nil)
        val envC = env.clone()
        envC(varName) = c
        val bodyC = encodeFromSmtTerm(ctx, body, envC)
        ctx.assertions += Z3Expr.And(List(Z3Expr.SetMember(c, setZ), bodyC))
        val wName = ctx.freshSkolem(s"the_set_witness_$varName")
        val w     = Z3Expr.Var(wName, elemSort)
        val envW  = env.clone()
        envW(varName) = w
        val bodyW = encodeFromSmtTerm(ctx, body, envW)
        ctx.assertions += Z3Expr.Quantifier(
          QKind.ForAll,
          List(Z3Binding(wName, elemSort)),
          Z3Expr.Implies(
            Z3Expr.And(List(Z3Expr.SetMember(w, setZ), bodyW)),
            Z3Expr.Cmp(CmpOp.Eq, w, c)
          )
        )
        c

      case TTheRel(varName, rel, body) =>
        ctx.state.get(rel) match
          case Some(_: StateRelationInfo)
              if env.values.exists { case _: Z3Expr.Var => true; case _ => false } =>
            // a Skolem constant only denotes "the unique element" at quantifier-free
            // position; under a binder it would need a Skolem function of the bound vars
            fail(ctx, "definite description `the` is not supported under a quantifier")
          case Some(r: StateRelationInfo) =>
            val mode       = ctx.stateMode
            val sort       = r.keySort
            val skolemName = ctx.freshSkolem(s"the_$rel")
            ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, sort))
            val c    = Z3Expr.App(skolemName, Nil)
            val envC = env.clone()
            envC(varName) = c
            val bodyC = encodeFromSmtTerm(ctx, body, envC)
            ctx.assertions += Z3Expr.And(List(Z3Expr.App(domFuncFor(r, mode), List(c)), bodyC))
            val wName = ctx.freshSkolem(s"the_witness_$rel")
            val w     = Z3Expr.Var(wName, sort)
            val envW  = env.clone()
            envW(varName) = w
            val bodyW = encodeFromSmtTerm(ctx, body, envW)
            ctx.assertions += Z3Expr.Quantifier(
              QKind.ForAll,
              List(Z3Binding(wName, sort)),
              Z3Expr.Implies(
                Z3Expr.And(List(Z3Expr.App(domFuncFor(r, mode), List(w)), bodyW)),
                Z3Expr.Cmp(CmpOp.Eq, w, c)
              )
            )
            c
          case _ =>
            fail(
              ctx,
              s"definite description `the` requires a state-relation domain; '$rel' is not one"
            )

      case TEntityBase(name) =>
        // `Entity{...}` desugars (in `lower`) to a WithRec chain over this base;
        // the base is a fresh entity of the named sort, the field updates are set
        // by the surrounding TWithRec encoding
        ctx.entities.get(name) match
          case Some(_) =>
            val skolemName = ctx.freshSkolem(s"construct_$name")
            ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
            Z3Expr.App(skolemName, Nil)
          case None =>
            fail(ctx, s"entity constructor for unknown entity '$name'")

      case TIndexRel(base, key) =>
        peelRelationRef(base, ctx.stateMode) match
          case Some((rel, mode)) =>
            ctx.state.get(rel) match
              case Some(r: StateRelationInfo) =>
                Z3Expr.App(mapFuncFor(r, mode), List(encodeFromSmtTerm(ctx, key, env)))
              case _ =>
                fail(ctx, s"indexing '$rel' requires a state relation")
          case None =>
            fail(ctx, "indexing requires an identifier (optionally wrapped in pre/prime) as base")
      case TFieldAccess(base, fname) =>
        val baseZ    = encodeFromSmtTerm(ctx, base, env)
        val baseSort = inferSortOfZ3Expr(ctx, baseZ)
        baseSort match
          case Some(Z3Sort.Uninterp(name)) =>
            ctx.entities.get(name) match
              case Some(entity) =>
                entity.fields.get(fname) match
                  case Some((_, funcName)) => Z3Expr.App(funcName, List(baseZ))
                  case None                => fail(ctx, s"entity '$name' has no field '$fname'")
              case None => fail(ctx, "field access requires entity sort")
          case _ => fail(ctx, "field access requires entity sort")

      case TSetEmpty() =>
        fail(ctx, "empty set literal requires context to infer element sort")
      case ins @ TSetInsert(_, _) =>
        val (elemSort, members) = collectSetLiteralMembers(ctx, ins, env)
        Z3Expr.SetLit(elemSort, members)
      case TSetMember(elem, set) =>
        val elemZ    = encodeFromSmtTerm(ctx, elem, env)
        val elemSort = inferSortOfZ3Expr(ctx, elemZ)
        set match
          case TSetEmpty() => Z3Expr.BoolLit(false)
          case ins: TSetInsert =>
            val members =
              collectSetLiteralMembersTerms(ins).map(t => encodeFromSmtTerm(ctx, t, env))
            for s <- elemSort; m <- members; ms <- inferSortOfZ3Expr(ctx, m) do
              if !Z3Sort.eq(s, ms) then
                fail(
                  ctx,
                  s"membership operator 'in' requires the left-hand side sort to match the set's element sort; got $s vs $ms"
                )
            members match
              case Nil => Z3Expr.BoolLit(false)
              case _   => Z3Expr.Or(members.map(m => Z3Expr.Cmp(CmpOp.Eq, elemZ, m)))
          case _ =>
            val rhs    = encodeFromSmtTerm(ctx, set, env)
            val rhSort = inferSortOfZ3Expr(ctx, rhs)
            (elemSort, rhSort) match
              case (Some(Z3Sort.OptionOf(es)), Some(Z3Sort.SetOf(rs))) if Z3Sort.eq(es, rs) =>
                // optional-vs-base membership (e.g. `tag_filter in t.tags`, tag_filter: Option[T]):
                // `opt in S` iff some x in S has opt = some(x) (none is never a member).
                val xName = ctx.freshSkolem("opt_in")
                val xVar  = Z3Expr.Var(xName, rs)
                Z3Expr.Quantifier(
                  QKind.Exists,
                  List(Z3Binding(xName, rs)),
                  Z3Expr.And(List(
                    Z3Expr.SetMember(xVar, rhs),
                    Z3Expr.Cmp(CmpOp.Eq, elemZ, Z3Expr.OptSome(xVar))
                  ))
                )
              case (Some(es), Some(Z3Sort.SetOf(rs))) if !Z3Sort.eq(es, rs) =>
                fail(
                  ctx,
                  s"membership operator 'in' requires the left-hand side sort to match the set's element sort; got $es against a set of $rs"
                )
              case (_, Some(Z3Sort.SetOf(_))) => Z3Expr.SetMember(elemZ, rhs)
              case (_, Some(other)) =>
                fail(
                  ctx,
                  s"membership operator 'in' requires a set-typed right operand; got $other"
                )
              case _ => Z3Expr.SetMember(elemZ, rhs)
      case TSetUnion(l, r)     => encodeSetBinOp(ctx, SetOpKind.Union, l, r, env)
      case TSetIntersect(l, r) => encodeSetBinOp(ctx, SetOpKind.Intersect, l, r, env)
      case TSetDiff(l, r)      => encodeSetBinOp(ctx, SetOpKind.Diff, l, r, env)

      // Membership on a primed/pre relation: the prime/pre applies to the relation's
      // domain only, not to the element (encInDom encodes the element in the ambient
      // mode and takes only the domain/const lookup at the given mode).
      case TPrime(TInDom(rel, elem)) => encInDom(ctx, rel, elem, StateMode.Post, env)
      case TPre(TInDom(rel, elem))   => encInDom(ctx, rel, elem, StateMode.Pre, env)
      case TPrime(inner) =>
        withStateMode(ctx, StateMode.Post, () => encodeFromSmtTerm(ctx, inner, env))
      case TPre(inner) =>
        withStateMode(ctx, StateMode.Pre, () => encodeFromSmtTerm(ctx, inner, env))

      case TWithRec(base, fld, value) =>
        val baseZ    = encodeFromSmtTerm(ctx, base, env)
        val baseSort = inferSortOfZ3Expr(ctx, baseZ)
        baseSort match
          case Some(Z3Sort.Uninterp(name)) =>
            ctx.entities.get(name) match
              case None => fail(ctx, s"'with' on non-entity sort '$name'")
              case Some(entity) =>
                val skolemName = ctx.freshSkolem(s"with_$name")
                ctx.declareFunc(Z3FunctionDecl(skolemName, Nil, Z3Sort.Uninterp(name)))
                val skolemRef = Z3Expr.App(skolemName, Nil)
                // A bare `none` carries no element sort; in a record-update the target field's
                // declared sort supplies it (e.g. `completed_at = none`, completed_at: Option[DateTime]).
                val v = (value, entity.fields.get(fld).map(_._1)) match
                  case (TNone(), Some(Z3Sort.OptionOf(elem))) => Z3Expr.OptNone(elem)
                  case _                                      => encodeFromSmtTerm(ctx, value, env)
                for (fname, (_, funcName)) <- entity.fields do
                  if fname == fld then
                    ctx.assertions += Z3Expr.Cmp(
                      CmpOp.Eq,
                      Z3Expr.App(funcName, List(skolemRef)),
                      v
                    )
                  else
                    ctx.assertions += Z3Expr.Cmp(
                      CmpOp.Eq,
                      Z3Expr.App(funcName, List(skolemRef)),
                      Z3Expr.App(funcName, List(baseZ))
                    )
                skolemRef
          case _ => fail(ctx, s"'with' on field '$fld' requires an entity-sorted base")

      case TIte(c, a, b) =>
        Z3Expr.Ite(
          encodeFromSmtTerm(ctx, c, env),
          encodeFromSmtTerm(ctx, a, env),
          encodeFromSmtTerm(ctx, b, env)
        )
      case TNone() =>
        fail(
          ctx,
          "'none' literal requires an optional-typed context (e.g. a comparison against an optional value)"
        )
      case TSome(t) =>
        Z3Expr.OptSome(encodeFromSmtTerm(ctx, t, env))
      case TStrLit(s) =>
        Z3Expr.StrLit(s)
      case TMatches(t, pat) =>
        val strZ    = encodeFromSmtTerm(ctx, t, env)
        val strSort = inferSortOfZ3Expr(ctx, strZ)
        (strSort, RegexParser.parse(pat)) match
          case (Some(Z3Sort.Str), Some(re)) => Z3Expr.InRe(strZ, re)
          case _                            =>
            // `str.in_re` needs a String operand and a supported pattern; for a
            // refinement-alias sort (modelled as its own sort) or an unsupported
            // pattern, fall back to the sound uninterpreted matcher
            val s        = strSort.getOrElse(Z3Sort.Str)
            val funcName = s"${ctx.matchesNameFor(pat)}_${sortNameOf(s)}"
            if !ctx.funcs.contains(funcName) then
              ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Bool))
            Z3Expr.App(funcName, List(strZ))
      case TUStrPred(name, t) =>
        val strZ     = encodeFromSmtTerm(ctx, t, env)
        val s        = inferSortOfZ3Expr(ctx, strZ).getOrElse(Z3Sort.Str)
        val funcName = s"${name}_${sortNameOf(s)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Bool))
        Z3Expr.App(funcName, List(strZ))
      // 1-arg reserved builtin string function (e.g. hash(x)): an uninterpreted Str-valued function.
      // Same name mangling as TUStrPred; determinism (same input -> same output) is the functionality.
      case TUStrFunc(name, t) =>
        val strZ     = encodeFromSmtTerm(ctx, t, env)
        val s        = inferSortOfZ3Expr(ctx, strZ).getOrElse(Z3Sort.Str)
        val funcName = s"${name}_${sortNameOf(s)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Str))
        Z3Expr.App(funcName, List(strZ))
      // 1-arg reserved builtin int function (e.g. days(n)): an uninterpreted Int-valued function.
      // Same name mangling as TUStrFunc; determinism (same input -> same output) is the functionality.
      case TUIntFunc(name, t) =>
        val argZ     = encodeFromSmtTerm(ctx, t, env)
        val s        = inferSortOfZ3Expr(ctx, argZ).getOrElse(Z3Sort.Int)
        val funcName = s"${name}_${sortNameOf(s)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Int))
        Z3Expr.App(funcName, List(argZ))
      // len(s): an uninterpreted Int-valued function, like the builtins above. Native Z3 str.len
      // forces the string solver to materialise concrete strings of the constrained length (e.g.
      // `len(token) = 128` with `distinct` tokens), which blows up by ~75x on string-heavy specs;
      // the functional dependency (same string -> same length) is all the refinement constraints
      // (`= 64`, `>= 8`) need, and `len` is vacuous-on-eval so the proof does not constrain this.
      case TStrLen(t) =>
        val argZ     = encodeFromSmtTerm(ctx, t, env)
        val s        = inferSortOfZ3Expr(ctx, argZ).getOrElse(Z3Sort.Str)
        val funcName = s"len_${sortNameOf(s)}"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Int))
        Z3Expr.App(funcName, List(argZ))
      // 0-arg reserved builtin (e.g. now()): an uninterpreted Int constant. The `${name}_0`
      // name matches translateCall's argSortsMangled, so the in-subset and raw paths share it.
      case TUConst(name) =>
        val funcName = s"${name}_0"
        if !ctx.funcs.contains(funcName) then
          ctx.declareFunc(Z3FunctionDecl(funcName, Nil, Z3Sort.Int))
        Z3Expr.App(funcName, Nil)
      // sum(coll, i => body): an uninterpreted Int-valued function keyed by the lambda body and the
      // collection sort. Same collection + same body => same sum (a functional dependency), so
      // equality invariants like `subtotal = sum(items, _)` are preserved when the collection is
      // unchanged. The body (translated, closed over the binder) is a structural key only -- it is
      // not summed; the arithmetic value semantics is not modelled (no finite-sum theory in SMT).
      case TSum(coll, body) =>
        val collZ = encodeFromSmtTerm(ctx, coll, env)
        inferSortOfZ3Expr(ctx, collZ) match
          case Some(s) =>
            val funcName = s"aggsum_${ctx.aggSumKeyFor(body.toString)}_${sortNameOf(s)}"
            if !ctx.funcs.contains(funcName) then
              ctx.declareFunc(Z3FunctionDecl(funcName, List(s), Z3Sort.Int))
            Z3Expr.App(funcName, List(collZ))
          case None =>
            fail(ctx, "cannot infer collection sort for sum aggregate")
      case TSeqEmpty() =>
        fail(ctx, "empty sequence literal requires context to infer its element sort")
      case cons @ TSeqCons(_, _) =>
        val members = collectSeqMembersTerms(cons).map(t => encodeFromSmtTerm(ctx, t, env))
        members match
          case head :: _ =>
            inferSortOfZ3Expr(ctx, head) match
              case Some(es) => Z3Expr.SeqLit(es, members)
              case None     => fail(ctx, "cannot infer sequence element sort")
          case Nil =>
            fail(ctx, "empty sequence literal requires context to infer its element sort")
      case TMapEmpty() =>
        fail(ctx, "empty map literal requires context to infer its key/value sorts")
      case cons @ TMapCons(_, _, _) =>
        val entries = collectMapEntriesTerms(cons).map { case (k, v) =>
          (encodeFromSmtTerm(ctx, k, env), encodeFromSmtTerm(ctx, v, env))
        }
        entries match
          case (hk, hv) :: _ =>
            (inferSortOfZ3Expr(ctx, hk), inferSortOfZ3Expr(ctx, hv)) match
              case (Some(ks), Some(vs)) => Z3Expr.MapLit(ks, vs, entries)
              case _                    => fail(ctx, "cannot infer map key/value sorts")
          case Nil =>
            fail(ctx, "empty map literal requires context to infer its key/value sorts")

  private[z3] def encodeNoneEq(
      ctx: TranslateCtx,
      operand: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val oz = encodeFromSmtTerm(ctx, operand, env)
    inferSortOfZ3Expr(ctx, oz) match
      case Some(Z3Sort.OptionOf(elem)) => Z3Expr.Cmp(CmpOp.Eq, oz, Z3Expr.OptNone(elem))
      case other =>
        fail(ctx, s"'none' requires an optional-typed operand; got sort $other")

  private[z3] def collectSetLiteralMembersTerms(term: smt_term): List[smt_term] = term match
    case TSetEmpty()           => Nil
    case TSetInsert(elem, set) => elem :: collectSetLiteralMembersTerms(set)
    case _                     => Nil

  private[z3] def collectSeqMembersTerms(term: smt_term): List[smt_term] = term match
    case TSeqCons(elem, rest) => elem :: collectSeqMembersTerms(rest)
    case _                    => Nil

  private[z3] def collectMapEntriesTerms(term: smt_term): List[(smt_term, smt_term)] = term match
    case TMapCons(k, v, rest) => (k, v) :: collectMapEntriesTerms(rest)
    case _                    => Nil

  private[z3] def encodeSetBinOp(
      ctx: TranslateCtx,
      op: SetOpKind,
      l: smt_term,
      r: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): Z3Expr =
    val lz = encodeFromSmtTerm(ctx, l, env)
    val rz = encodeFromSmtTerm(ctx, r, env)
    val ls = inferSortOfZ3Expr(ctx, lz)
    val rs = inferSortOfZ3Expr(ctx, rz)
    (ls, rs) match
      case (Some(Z3Sort.SetOf(le)), Some(Z3Sort.SetOf(re))) =>
        if !Z3Sort.eq(le, re) then
          fail(
            ctx,
            s"set operator '${SetOpKind.token(op)}' requires both operands to have the same element sort; got $le vs $re"
          )
        Z3Expr.SetBinOp(op, lz, rz)
      case (Some(_), Some(_)) =>
        fail(
          ctx,
          s"set operator '${SetOpKind.token(op)}' requires both operands to be sets"
        )
      case _ =>
        Z3Expr.SetBinOp(op, lz, rz)

  private[z3] def quantifierDomainFor(
      ctx: TranslateCtx,
      name: String,
      varName: String
  ): (Z3Sort, Option[Z3Expr]) =
    ctx.state.get(name) match
      case Some(r: StateRelationInfo) =>
        val v = Z3Expr.Var(varName, r.keySort)
        (r.keySort, Some(Z3Expr.App(domFuncFor(r, ctx.stateMode), List(v))))
      case Some(c: StateConstInfo) =>
        c.sort match
          case Z3Sort.SetOf(elem) =>
            val v = Z3Expr.Var(varName, elem)
            (elem, Some(Z3Expr.SetMember(v, Z3Expr.App(constFuncFor(c, ctx.stateMode), Nil))))
          case other => (other, None)
      case None =>
        ctx.entities.get(name).map(e => (e.sort, None))
          .orElse(ctx.enums.get(name).map(e => (e.sort, None)))
          .getOrElse((Z3Sort.Uninterp("Unknown"), None))

  private[z3] def collectSetLiteralMembers(
      ctx: TranslateCtx,
      term: smt_term,
      env: mutable.Map[String, Z3Expr]
  ): (Z3Sort, List[Z3Expr]) =
    val terms   = collectSetLiteralMembersTerms(term)
    val encoded = terms.map(t => encodeFromSmtTerm(ctx, t, env))
    val sorts   = encoded.map(t => inferSortOfZ3Expr(ctx, t))
    val unknown = sorts.indexWhere(_.isEmpty)
    if unknown >= 0 then
      fail(ctx, s"set literal element has unknown sort: ${terms(unknown)}")
    val knownSorts = sorts.flatten
    val elemSort   = knownSorts.head
    val mismatch   = knownSorts.indexWhere(s => !Z3Sort.eq(s, elemSort))
    if mismatch >= 0 then
      fail(ctx, "set literal elements must all have the same sort")
    (elemSort, encoded)
