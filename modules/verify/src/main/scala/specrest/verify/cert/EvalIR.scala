package specrest.verify.cert

import specrest.ir.*

object EvalIR:

  enum Value derives CanEqual:
    case VBool(b: Boolean)
    case VInt(n: BigInt)
    case VEnum(enumName: String, memberName: String)
    case VEntity(entityName: String, id: String)
    case VSet(members: List[Value])
    // M_L.4.b-ext Phase 4b: Skolem chain carrier for record-update. `With`
    // lowers to a left-fold of VEntityWith over the original entity value;
    // `fieldLookup` walks the chain (override-first, fall back to base).
    // Mirrors Lean `Value.vEntityWith` in `proofs/lean/SpecRest/Semantics.lean`.
    case VEntityWith(base: Value, fld: String, value: Value)

  type Env = List[(String, Value)]

  /** M_L.4.b-ext Phase 5.b: pre/post mode selector. Mirrors Lean `SpecRest.Semantics.StateMode` in
    * `proofs/lean/SpecRest/Semantics.lean`.
    *
    * Used by `evalAt` to thread the active state through state-touching ops. `Expr.Prime` flips
    * mode to `Post`; `Expr.Pre` flips mode to `Pre`. The innermost temporal operator wins (e.g.,
    * `Prime(Pre(x))` reads `x` from the pre-state).
    */
  enum StateMode derives CanEqual:
    case Pre
    case Post

  final case class Schema(
      enums: List[(String, List[String])],
      entities: List[String]
  )

  object Schema:
    val empty: Schema = Schema(Nil, Nil)

    def of(ir: ServiceIR): Schema =
      Schema(
        enums = ir.enums.map(d => (d.name, d.values)),
        entities = ir.entities.map(_.name)
      )

  final case class State(
      scalars: List[(String, Value)],
      relations: List[(String, List[Value])],
      lookups: List[(String, List[(Value, Value)])],
      entityFields: List[(String, List[(String, Value)])]
  )

  object State:
    val empty: State = State(Nil, Nil, Nil, Nil)

    def demo(ir: ServiceIR): State =
      val schema      = Schema.of(ir)
      val stateFields = ir.state.fold(List.empty[StateFieldDecl])(_.fields)
      val (scalarFields, relationFields) =
        stateFields.partition(f => isScalarType(f.typeExpr))
      // M_L.4.k: entity-typed scalars are seeded as `vEntity name <fresh-id>`,
      // and the entityFields table is keyed by that fresh id. Seeding is
      // **recursive**: when an entity field is itself entity-typed (e.g.,
      // `User.profile : Profile`), allocate a nested id and register that
      // child instance's fields too. Without recursion, `current_user.profile`
      // would evaluate to `vEntity Profile ""` with no entityFields entry, so
      // chained `current_user.profile.email` would bottom out as `none` (the
      // exact case coderabbit flagged on PR #197). We bound recursion at depth
      // ≤ 4 to avoid divergence on cyclic schemas (e.g., `User.parent : User`)
      // — concrete chains in real fixtures stay well under this.
      val seedingDepth = 4
      val (scalars, entityFields) = scalarFields.foldLeft(
        (List.empty[(String, Value)], List.empty[(String, List[(String, Value)])])
      ): (acc, f) =>
        val (scAcc, fldAcc) = acc
        f.typeExpr match
          case TypeExpr.NamedType(entityName, _) if schema.entities.contains(entityName) =>
            val entityId             = s"${f.name}__id"
            val (rootFields, nested) = seedEntity(ir, schema, entityName, entityId, seedingDepth)
            (
              scAcc :+ (f.name, Value.VEntity(entityName, entityId)),
              (fldAcc :+ ((entityId, rootFields))) ++ nested
            )
          case _ =>
            (scAcc :+ (f.name, defaultFor(schema, f.typeExpr)), fldAcc)
      // Relation domains are left empty in demo synthesis. The trade-off was
      // measured during PR #197 against the auth_service / todo_list /
      // url_shortener fixtures:
      //   - Empty domains let `forallRel` short-circuit to `Some(VBool true)`,
      //     flipping many `forall x in rel, …` obligations to `cert_decide`
      //     even when the body itself contains nested FieldAccess we can't
      //     yet evaluate honestly.
      //   - A seeded one-element (key, value) row regressed: `forallRel`
      //     iterates over relation **values** (the verified-subset Lean
      //     embedding), so binding `s` to a value-entity and then doing
      //     `Index(rel, s)` finds nothing in the lookups table (which is
      //     keyed on the relation's key type). Body fails, outer fails, the
      //     short-circuit win is lost without any new cert flipping.
      // Honest non-vacuous demo state for `Index(rel, key)` requires
      // distinguishing the "iterate keys" vs "iterate values" semantics on
      // the verified-subset embedding's `forallRel`, plus a schema-aware
      // (key, value-entity) row that lines up with both. That alignment is
      // out of scope for M_L.4.k — left for a follow-up slice.
      //
      // For chained FieldAccess on state scalars (`current_user.profile.email`
      // and the like), recursive entity seeding above handles the chain
      // without touching the relation tables.
      val setRelationFields = stateFields.filter:
        case StateFieldDecl(_, TypeExpr.SetType(_, _), _) => true
        case _                                            => false
      val relationCarrierFields = (relationFields ++ setRelationFields).distinctBy(_.name)
      val relations             = relationCarrierFields.map(f => (f.name, List.empty[Value]))
      val lookups               = relationCarrierFields.map(f => (f.name, List.empty[(Value, Value)]))
      State(scalars, relations, lookups, entityFields)

    private def isScalarType(ty: TypeExpr): Boolean = ty match
      case _: TypeExpr.NamedType => true
      case _: TypeExpr.SetType   => true
      case _                     => false

    /** Recursively materialise an entity instance and its nested entity-typed fields into the
      * entityFields table. Each entity-typed field gets a fresh id; non-entity fields fall back to
      * `defaultFor`. Returns the root instance's field list plus the cumulative list of nested
      * instance entries (id → field-list pairs). Depth-bounded so cyclic schemas terminate.
      */
    private def seedEntity(
        ir: ServiceIR,
        schema: Schema,
        entityName: String,
        entityId: String,
        depth: Int
    ): (List[(String, Value)], List[(String, List[(String, Value)])]) =
      val decl = ir.entities.find(_.name == entityName)
      decl match
        case None => (List.empty, List.empty)
        case Some(d) =>
          d.fields.foldLeft(
            (List.empty[(String, Value)], List.empty[(String, List[(String, Value)])])
          ): (acc, fld) =>
            val (rootAcc, nestedAcc) = acc
            fld.typeExpr match
              case TypeExpr.NamedType(childName, _)
                  if schema.entities.contains(childName) && depth > 0 =>
                val childId = s"${entityId}__${fld.name}"
                val (childFields, deeperNested) =
                  seedEntity(ir, schema, childName, childId, depth - 1)
                (
                  rootAcc :+ (fld.name, Value.VEntity(childName, childId)),
                  (nestedAcc :+ ((childId, childFields))) ++ deeperNested
                )
              case _ =>
                (rootAcc :+ (fld.name, defaultFor(schema, fld.typeExpr)), nestedAcc)

  /** M_L.4.b-ext Phase 5.b: two-state carrier. Mirrors Lean `SpecRest.Semantics.StatePair` in
    * `proofs/lean/SpecRest/Semantics.lean`.
    *
    * Operation contracts evaluate against a pre/post pair: the pre-state holds `count`-style
    * identifiers; `count'` (rendered as `Expr.Prime`) reaches the post-state. Single-state
    * invariants use `StatePair.diag(st)` and rely on the diagonal-collapse property: `evalAt(mode,
    * _, diag(st), _, _)` agrees with `eval(_, st, _, _)` for every mode (mirrors Lean
    * `evalAt_diagonal_eq_eval`).
    */
  final case class StatePair(pre: State, post: State):
    def at(mode: StateMode): State = mode match
      case StateMode.Pre  => pre
      case StateMode.Post => post

  object StatePair:
    val empty: StatePair = StatePair(State.empty, State.empty)

    /** Diagonal lift: same state in both modes. Phase 5.b emission uses this for state-only
      * invariants so the existing single-state cert path stays intact; Phase 5.c will produce
      * honest non-diagonal pairs for operation invariant-preservation certs.
      */
    def diag(st: State): StatePair = StatePair(st, st)

    /** Demo synthesis. For Phase 5.b this is a diagonal pair; Phase 5.c will specialise `post`
      * per-operation by applying ensures-style updates. The shape stays the same so call sites
      * don't churn.
      */
    def demo(ir: ServiceIR): StatePair = diag(State.demo(ir))

  /** Default value chosen by the demo-state synthesizer for a given typeExpr. Threads `Schema` so
    * entity-typed scalars get `.vEntity` and enum-typed scalars get `.vEnum` rather than the wrong
    * constructor.
    */
  def defaultFor(s: Schema, ty: TypeExpr): Value = ty match
    case TypeExpr.NamedType("Int", _)  => Value.VInt(BigInt(0))
    case TypeExpr.NamedType("Bool", _) => Value.VBool(false)
    case TypeExpr.NamedType(name, _) =>
      if s.entities.contains(name) then Value.VEntity(name, "")
      else
        s.enums.collectFirst { case (n, members) if n == name => members } match
          case Some(member :: _) => Value.VEnum(name, member)
          case _                 => Value.VBool(false)
    case _: TypeExpr.SetType => Value.VSet(Nil)
    case _                   => Value.VBool(false)

  def envLookup(env: Env, name: String): Option[Value] =
    env.collectFirst { case (k, v) if k == name => v }

  def stateScalar(st: State, name: String): Option[Value] =
    st.scalars.collectFirst { case (k, v) if k == name => v }

  def relationDomain(st: State, name: String): Option[List[Value]] =
    st.relations.collectFirst { case (k, vs) if k == name => vs }

  def relationPairs(st: State, name: String): Option[List[(Value, Value)]] =
    st.lookups.collectFirst { case (k, ps) if k == name => ps }

  private def valueEq(l: Value, r: Value): Boolean = (l, r) match
    case (Value.VSet(left), Value.VSet(right)) =>
      left.forall(right.contains) && right.forall(left.contains)
    case _ => l == r

  private def containsValue(values: List[Value], needle: Value): Boolean =
    values.exists(valueEq(_, needle))

  def lookupKey(st: State, relName: String, key: Value): Option[Value] =
    relationPairs(st, relName).flatMap: pairs =>
      pairs.collectFirst { case (k, v) if valueEq(k, key) => v }

  def lookupField(st: State, entityId: String, fieldName: String): Option[Value] =
    st.entityFields.collectFirst { case (k, fs) if k == entityId => fs }
      .flatMap(fs => fs.collectFirst { case (f, v) if f == fieldName => v })

  /** M_L.4.b-ext Phase 4b: walk a (potentially With-extended) value chain. Mirrors Lean
    * `Value.fieldLookup` in `proofs/lean/SpecRest/Semantics.lean`:
    *   - VEntityWith override-then-fallback,
    *   - VEntity routes to `lookupField`,
    *   - non-entity / non-with values return None.
    */
  def fieldLookup(st: State, v: Value, fieldName: String): Option[Value] = v match
    case Value.VEntity(_, id) => lookupField(st, id, fieldName)
    case Value.VEntityWith(base, f, ov) =>
      if fieldName == f then Some(ov)
      else fieldLookup(st, base, fieldName)
    case _ => None

  def asBool(v: Value): Option[Boolean] = v match
    case Value.VBool(b) => Some(b)
    case _              => None

  def asInt(v: Value): Option[BigInt] = v match
    case Value.VInt(n) => Some(n)
    case _             => None

  def asSet(v: Value): Option[List[Value]] = v match
    case Value.VSet(members) => Some(members)
    case _                   => None

  private def dedupeValues(values: List[Value]): List[Value] =
    values.foldRight(List.empty[Value]): (v, acc) =>
      if containsValue(acc, v) then acc else v :: acc

  private def setUnionValues(l: List[Value], r: List[Value]): List[Value] =
    dedupeValues(l ++ r)

  private def setIntersectValues(l: List[Value], r: List[Value]): List[Value] =
    dedupeValues(l.filter(v => containsValue(r, v)))

  private def setDiffValues(l: List[Value], r: List[Value]): List[Value] =
    dedupeValues(l.filterNot(v => containsValue(r, v)))

  def evalBoolBin(op: BinOp, a: Boolean, b: Boolean): Option[Boolean] = op match
    case BinOp.And     => Some(a && b)
    case BinOp.Or      => Some(a || b)
    case BinOp.Implies => Some(!a || b)
    case BinOp.Iff     => Some(a == b)
    case _             => None

  def evalArith(op: BinOp, l: Value, r: Value): Option[Value] =
    (asInt(l), asInt(r)) match
      case (Some(a), Some(b)) =>
        op match
          case BinOp.Add => Some(Value.VInt(a + b))
          case BinOp.Sub => Some(Value.VInt(a - b))
          case BinOp.Mul => Some(Value.VInt(a * b))
          case BinOp.Div =>
            // SMT-LIB Int `div` uses Euclidean division (`0 ≤ mod a b < |b|`).
            // BigInt./ truncates toward zero, which differs for negative
            // operands. Match Lean's `Int.ediv` (used by smtEval/eval) so cert
            // values agree.
            if b == BigInt(0) then None
            else
              val q = a / b
              val r = a % b
              val euclideanQ =
                if r < BigInt(0) then if b > BigInt(0) then q - 1 else q + 1
                else q
              Some(Value.VInt(euclideanQ))
          case _ => None
      case _ => None

  def evalCmp(op: BinOp, l: Value, r: Value): Option[Boolean] = op match
    case BinOp.Eq  => Some(valueEq(l, r))
    case BinOp.Neq => Some(!valueEq(l, r))
    case BinOp.Lt =>
      (asInt(l), asInt(r)) match
        case (Some(a), Some(b)) => Some(a < b)
        case _                  => None
    case BinOp.Le =>
      (asInt(l), asInt(r)) match
        case (Some(a), Some(b)) => Some(a <= b)
        case _                  => None
    case BinOp.Gt =>
      (asInt(l), asInt(r)) match
        case (Some(a), Some(b)) => Some(a > b)
        case _                  => None
    case BinOp.Ge =>
      (asInt(l), asInt(r)) match
        case (Some(a), Some(b)) => Some(a >= b)
        case _                  => None
    case _ => None

  def evalSetBin(op: BinOp, l: Value, r: Value): Option[Value] =
    (asSet(l), asSet(r)) match
      case (Some(a), Some(b)) =>
        op match
          case BinOp.Union     => Some(Value.VSet(setUnionValues(a, b)))
          case BinOp.Intersect => Some(Value.VSet(setIntersectValues(a, b)))
          case BinOp.Diff      => Some(Value.VSet(setDiffValues(a, b)))
          case _               => None
      case _ => None

  /** Single-state entry point. Delegates to `evalAt` with `mode = Pre` and a diagonal StatePair.
    * The diagonal-collapse property (mirror of Lean `evalAt_diagonal_eq_eval`) holds by definition:
    * in this configuration `Prime` and `Pre` both flip to a mode whose state slot is the same `st`,
    * so the result agrees with the prior single-state `eval` semantics.
    */
  def eval(s: Schema, st: State, env: Env, expr: Expr): Option[Value] =
    evalAt(StateMode.Pre, s, StatePair.diag(st), env, expr)

  /** M_L.4.b-ext Phase 5.b: mode-aware closed evaluator. Mirrors Lean `SpecRest.Semantics.evalAt`
    * in `proofs/lean/SpecRest/Semantics.lean`.
    *
    * State-touching arms (Identifier state-fallback, Cardinality, In/NotIn over a relation
    * identifier, Subset, Index, FieldAccess, Quantifier(All) over a relation domain) read from
    * `sp.at(mode)`. `Expr.Prime` flips mode to `Post`; `Expr.Pre` flips mode to `Pre`. All other
    * arms recurse through `evalAt` with the same `mode`/`sp`, so the temporal context propagates
    * down a sub-tree until the next Prime/Pre boundary.
    */
  def evalAt(
      mode: StateMode,
      s: Schema,
      sp: StatePair,
      env: Env,
      expr: Expr
  ): Option[Value] =
    val st = sp.at(mode)
    expr match
      case Expr.BoolLit(v, _) => Some(Value.VBool(v))
      case Expr.IntLit(v, _)  => Some(Value.VInt(BigInt(v)))
      case Expr.Identifier(name, _) =>
        envLookup(env, name).orElse(stateScalar(st, name))
      case Expr.UnaryOp(UnOp.Not, operand, _) =>
        evalAt(mode, s, sp, env, operand).flatMap(asBool).map(b => Value.VBool(!b))
      case Expr.UnaryOp(UnOp.Negate, operand, _) =>
        evalAt(mode, s, sp, env, operand).flatMap(asInt).map(n => Value.VInt(-n))
      case Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier(relName, _), _) =>
        relationDomain(st, relName).map(dom => Value.VInt(BigInt(dom.length)))
      case Expr.BinaryOp(op, l, r, _) if isBoolBinOp(op) =>
        for
          lv  <- evalAt(mode, s, sp, env, l)
          rv  <- evalAt(mode, s, sp, env, r)
          lb  <- asBool(lv)
          rb  <- asBool(rv)
          out <- evalBoolBin(op, lb, rb)
        yield Value.VBool(out)
      case Expr.BinaryOp(op, l, r, _) if isArithOp(op) =>
        for
          lv  <- evalAt(mode, s, sp, env, l)
          rv  <- evalAt(mode, s, sp, env, r)
          out <- evalArith(op, lv, rv)
        yield out
      case Expr.BinaryOp(op, l, r, _) if isCmpOp(op) =>
        for
          lv  <- evalAt(mode, s, sp, env, l)
          rv  <- evalAt(mode, s, sp, env, r)
          out <- evalCmp(op, lv, rv)
        yield Value.VBool(out)
      case Expr.BinaryOp(op, l, r, _) if isSetBinOp(op) =>
        for
          lv  <- evalAt(mode, s, sp, env, l)
          rv  <- evalAt(mode, s, sp, env, r)
          out <- evalSetBin(op, lv, rv)
        yield out
      case Expr.BinaryOp(BinOp.In, elem, Expr.Identifier(relName, _), _) =>
        evalAt(mode, s, sp, env, Expr.Identifier(relName)).flatMap(asSet) match
          case Some(members) =>
            evalAt(mode, s, sp, env, elem).map(v => Value.VBool(containsValue(members, v)))
          case None =>
            relationDomain(st, relName)
              .flatMap(dom =>
                evalAt(mode, s, sp, env, elem).map(v => Value.VBool(containsValue(dom, v)))
              )
      case Expr.BinaryOp(BinOp.In, elem, setExpr, _) =>
        for
          v       <- evalAt(mode, s, sp, env, elem)
          setVal  <- evalAt(mode, s, sp, env, setExpr)
          members <- asSet(setVal)
        yield Value.VBool(containsValue(members, v))
      case Expr.BinaryOp(BinOp.NotIn, elem, rel @ Expr.Identifier(_, _), _) =>
        evalAt(mode, s, sp, env, Expr.UnaryOp(UnOp.Not, Expr.BinaryOp(BinOp.In, elem, rel)))
      case Expr.BinaryOp(BinOp.NotIn, elem, setExpr, _) =>
        evalAt(mode, s, sp, env, Expr.UnaryOp(UnOp.Not, Expr.BinaryOp(BinOp.In, elem, setExpr)))
      case Expr.BinaryOp(
            BinOp.Subset,
            Expr.Identifier(r1, _),
            Expr.Identifier(r2, _),
            _
          ) =>
        // Subset(r1, r2)  ≡  ∀ x ∈ r1, x ∈ r2. Both relation domains are read
        // from the active mode's state.
        for
          dom1 <- relationDomain(st, r1)
          dom2 <- relationDomain(st, r2)
        yield Value.VBool(dom1.forall(v => containsValue(dom2, v)))
      case Expr.Index(Expr.Identifier(relName, _), keyExpr, _) =>
        evalAt(mode, s, sp, env, keyExpr).flatMap(kv => lookupKey(st, relName, kv))
      case Expr.FieldAccess(base, fieldName, _) =>
        evalAt(mode, s, sp, env, base).flatMap(v => fieldLookup(st, v, fieldName))
      case Expr.With(base, updates, _) =>
        evalAt(mode, s, sp, env, base).flatMap: bv =>
          updates.foldLeft[Option[Value]](Some(bv)): (acc, upd) =>
            for
              cur <- acc
              v   <- evalAt(mode, s, sp, env, upd.value)
            yield Value.VEntityWith(cur, upd.name, v)
      case Expr.Let(name, value, body, _) =>
        evalAt(mode, s, sp, env, value).flatMap(v =>
          evalAt(mode, s, sp, (name, v) :: env, body)
        )
      case Expr.EnumAccess(Expr.Identifier(enName, _), member, _) =>
        s.enums.collectFirst { case (n, members) if n == enName => members } match
          case Some(members) if members.contains(member) =>
            Some(Value.VEnum(enName, member))
          case _ => None
      case Expr.Quantifier(QuantKind.All, bindings, body, _) =>
        bindings match
          case List(QuantifierBinding(v, Expr.Identifier(name, _), _, _)) =>
            s.enums.collectFirst { case (n, members) if n == name => members } match
              case Some(members) =>
                evalForallEnumAt(mode, s, sp, env, v, name, members, body)
              case None =>
                relationDomain(st, name) match
                  case Some(dom) => evalForallRelAt(mode, s, sp, env, v, dom, body)
                  case None      => None
          case _ => None
      case Expr.Quantifier(QuantKind.No, bindings, body, _) =>
        evalAt(
          mode,
          s,
          sp,
          env,
          Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
        )
      case Expr.Quantifier(QuantKind.Some, bindings, body, _) =>
        evalAt(
          mode,
          s,
          sp,
          env,
          Expr.UnaryOp(
            UnOp.Not,
            Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
          )
        )
      case Expr.Quantifier(QuantKind.Exists, bindings, body, _) =>
        evalAt(
          mode,
          s,
          sp,
          env,
          Expr.UnaryOp(
            UnOp.Not,
            Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
          )
        )
      case Expr.Prime(inner, _) => evalAt(StateMode.Post, s, sp, env, inner)
      case Expr.Pre(inner, _)   => evalAt(StateMode.Pre, s, sp, env, inner)
      case Expr.SetLiteral(elements, _) =>
        val values = elements.map(evalAt(mode, s, sp, env, _))
        if values.exists(_.isEmpty) then None
        else Some(Value.VSet(dedupeValues(values.flatten)))
      case _ => None

  private def isBoolBinOp(op: BinOp): Boolean = op match
    case BinOp.And | BinOp.Or | BinOp.Implies | BinOp.Iff => true
    case _                                                => false

  private def isArithOp(op: BinOp): Boolean = op match
    case BinOp.Add | BinOp.Sub | BinOp.Mul | BinOp.Div => true
    case _                                             => false

  private def isCmpOp(op: BinOp): Boolean = op match
    case BinOp.Eq | BinOp.Neq | BinOp.Lt | BinOp.Le | BinOp.Gt | BinOp.Ge => true
    case _                                                                => false

  private def isSetBinOp(op: BinOp): Boolean = op match
    case BinOp.Union | BinOp.Intersect | BinOp.Diff => true
    case _                                          => false

  private def evalForallEnumAt(
      mode: StateMode,
      s: Schema,
      sp: StatePair,
      env: Env,
      v: String,
      enName: String,
      members: List[String],
      body: Expr
  ): Option[Value] =
    val results = members.map: m =>
      val extEnv = (v, Value.VEnum(enName, m)) :: env
      evalAt(mode, s, sp, extEnv, body).flatMap(asBool)
    if results.exists(_.isEmpty) then None
    else Some(Value.VBool(results.flatten.forall(identity)))

  private def evalForallRelAt(
      mode: StateMode,
      s: Schema,
      sp: StatePair,
      env: Env,
      v: String,
      dom: List[Value],
      body: Expr
  ): Option[Value] =
    val results = dom.map: value =>
      val extEnv = (v, value) :: env
      evalAt(mode, s, sp, extEnv, body).flatMap(asBool)
    if results.exists(_.isEmpty) then None
    else Some(Value.VBool(results.flatten.forall(identity)))

  /** Evaluate an invariant body and project to a boolean verdict. None means the closed reduction
    * got stuck (e.g., a state-relation reference that the demo state doesn't define).
    */
  def evalInvariantBody(
      ir: ServiceIR,
      st: State,
      env: Env,
      body: Expr
  ): Option[Boolean] =
    eval(Schema.of(ir), st, env, body).flatMap(asBool)

  /** M_L.4.b-ext Phase 5.b: mode-aware variant of `evalInvariantBody`. Evaluates a body against a
    * `StatePair` with the active `mode`, surfacing temporal (`Prime`/`Pre`) flips through the inner
    * `evalAt`. Phase 5.c will use this for operation invariant-preservation certs.
    */
  def evalInvariantBodyAt(
      mode: StateMode,
      ir: ServiceIR,
      sp: StatePair,
      env: Env,
      body: Expr
  ): Option[Boolean] =
    evalAt(mode, Schema.of(ir), sp, env, body).flatMap(asBool)

  /** Mode-aware variant of `evalRequires`. Evaluates the conjunction of a list of `requires`
    * clauses against the active mode of `sp`. Short-circuits on the first `false` or `none`.
    */
  def evalRequiresAt(
      mode: StateMode,
      ir: ServiceIR,
      sp: StatePair,
      env: Env,
      requires: List[Expr]
  ): Option[Boolean] =
    requires.foldLeft[Option[Boolean]](Some(true)): (acc, r) =>
      acc match
        case Some(true) => evalInvariantBodyAt(mode, ir, sp, env, r)
        case other      => other

  def evalRequires(
      ir: ServiceIR,
      st: State,
      env: Env,
      requires: List[Expr]
  ): Option[Boolean] =
    requires.foldLeft[Option[Boolean]](Some(true)): (acc, r) =>
      acc match
        case Some(true) => evalInvariantBody(ir, st, env, r)
        case other      => other

  /** M_L.4.b-ext Phase 5.c: result of analysing an operation's `ensures` for the post-state
    * synthesizer. The synthesizer recognises only the "primed-equality" pattern
    * (`Prime(Identifier(name)) = rhs`, or symmetric) with no `Prime` in the right-hand side.
    * `Recognized` carries the assignments the synthesizer extracted; `Unrecognized` carries a
    * one-line reason for the cert renderer's stub message. Unrecognized clauses cause the
    * synthesizer to decline rather than silently produce a half-synthesized post-state, since a
    * partial post would risk false-positive preservation certs.
    */
  enum EnsuresAnalysis derives CanEqual:
    case Recognized(assignments: List[(String, Value)])
    case Unrecognized(reason: String)

  /** Analyse an operation's `ensures` clauses against the pre-state. Returns `Recognized` only when
    * EVERY clause (after `And`-flattening) matches the primed-equality pattern with a
    * closed-evaluable RHS; otherwise returns `Unrecognized` with a short shape diagnosis.
    */
  def analyseEnsures(
      ir: ServiceIR,
      op: OperationDecl,
      pre: State
  ): EnsuresAnalysis =
    val schema  = Schema.of(ir)
    val flatten = op.ensures.flatMap(flattenAnd)
    val results = flatten.map(extractAssignment(_, schema, pre, Nil))
    results.find(_.isLeft) match
      case Some(Left(reason)) => EnsuresAnalysis.Unrecognized(reason)
      case _ =>
        val asgns = results.collect { case Right(a) => a }.flatten
        EnsuresAnalysis.Recognized(asgns)

  /** Synthesize the post-state by applying `Recognized` assignments on top of `pre`. Each
    * assignment overrides the named scalar's value; un-named scalars keep their pre-state value.
    * Relations / lookups / entityFields carry through unchanged — Phase 5.c's synthesis is
    * scalar-only. Operations touching relations or entity fields will surface as `Unrecognized`.
    */
  def synthesizePostState(
      ir: ServiceIR,
      op: OperationDecl,
      pre: State
  ): Option[State] =
    analyseEnsures(ir, op, pre) match
      case EnsuresAnalysis.Unrecognized(_) => None
      case EnsuresAnalysis.Recognized(assignments) =>
        val newScalars = assignments.foldLeft(pre.scalars): (acc, asgn) =>
          val (name, newValue) = asgn
          acc.map {
            case (k, _) if k == name => (k, newValue)
            case kv                  => kv
          }
        Some(pre.copy(scalars = newScalars))

  private def flattenAnd(expr: Expr): List[Expr] = expr match
    case Expr.BinaryOp(BinOp.And, l, r, _) => flattenAnd(l) ++ flattenAnd(r)
    case other                             => List(other)

  /** Try to extract a `(name, value)` assignment from a single ensures clause. `Right(Some(_))`
    * means a recognised assignment; `Right(None)` means a recognised non-assignment that the
    * synthesizer can ignore (e.g., `BoolLit(true)`); `Left(reason)` means an unrecognised shape
    * that should abort the synthesis.
    */
  private def extractAssignment(
      expr: Expr,
      schema: Schema,
      pre: State,
      env: Env
  ): Either[String, Option[(String, Value)]] = expr match
    case Expr.BoolLit(true, _) => Right(None)
    case Expr.BinaryOp(BinOp.Eq, lhs, rhs, _) =>
      primedAssignment(lhs, rhs, schema, pre, env)
        .orElse(primedAssignment(rhs, lhs, schema, pre, env)) match
        case Some(asgn) => Right(Some(asgn))
        case None =>
          Left(s"unrecognised ensures shape: $expr (expected `name' = rhs`)")
    case _ =>
      Left(s"unrecognised ensures shape: $expr (expected `name' = rhs`)")

  private def primedAssignment(
      primedSide: Expr,
      valueSide: Expr,
      schema: Schema,
      pre: State,
      env: Env
  ): Option[(String, Value)] = primedSide match
    case Expr.Prime(Expr.Identifier(name, _), _) if !containsPrime(valueSide) =>
      eval(schema, pre, env, valueSide).map(v => (name, v))
    case _ => None

  /** Conservative `Prime` detector. Used to reject ensures clauses whose RHS mentions a primed
    * identifier — the synthesizer can't resolve them without fixed-point reasoning, and silently
    * treating `count'` as `pre.count` (via diagonal collapse) would produce misleading post-states.
    */
  def containsPrime(expr: Expr): Boolean = expr match
    case _: Expr.Prime                  => true
    case Expr.Pre(_, _)                 => false
    case Expr.UnaryOp(_, op, _)         => containsPrime(op)
    case Expr.BinaryOp(_, l, r, _)      => containsPrime(l) || containsPrime(r)
    case Expr.Quantifier(_, _, body, _) => containsPrime(body)
    case Expr.Let(_, value, body, _)    => containsPrime(value) || containsPrime(body)
    case Expr.Index(base, idx, _)       => containsPrime(base) || containsPrime(idx)
    case Expr.FieldAccess(base, _, _)   => containsPrime(base)
    case Expr.With(base, updates, _) =>
      containsPrime(base) || updates.exists(u => containsPrime(u.value))
    case Expr.SetLiteral(elems, _)   => elems.exists(containsPrime)
    case Expr.EnumAccess(base, _, _) => containsPrime(base)
    case Expr.SetComprehension(_, dom, body, _) =>
      containsPrime(dom) || containsPrime(body)
    case Expr.SomeWrap(inner, _)   => containsPrime(inner)
    case Expr.The(_, dom, body, _) => containsPrime(dom) || containsPrime(body)
    case _                         => false
