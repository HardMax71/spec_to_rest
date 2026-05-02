package specrest.verify.cert

import specrest.ir.*

object EvalIR:

  enum Value derives CanEqual:
    case VBool(b: Boolean)
    case VInt(n: BigInt)
    case VEnum(enumName: String, memberName: String)
    case VEntity(entityName: String, id: String)

  type Env = List[(String, Value)]

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
      val schema = Schema.of(ir)
      val (scalarFields, relationFields) = ir.state match
        case None       => (Nil, Nil)
        case Some(decl) => decl.fields.partition(f => isScalarType(f.typeExpr))
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
      val relations = relationFields.map(f => (f.name, List.empty[Value]))
      val lookups   = relationFields.map(f => (f.name, List.empty[(Value, Value)]))
      State(scalars, relations, lookups, entityFields)

    private def isScalarType(ty: TypeExpr): Boolean = ty match
      case _: TypeExpr.NamedType => true
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
    case _ => Value.VBool(false)

  def envLookup(env: Env, name: String): Option[Value] =
    env.collectFirst { case (k, v) if k == name => v }

  def stateScalar(st: State, name: String): Option[Value] =
    st.scalars.collectFirst { case (k, v) if k == name => v }

  def relationDomain(st: State, name: String): Option[List[Value]] =
    st.relations.collectFirst { case (k, vs) if k == name => vs }

  def relationPairs(st: State, name: String): Option[List[(Value, Value)]] =
    st.lookups.collectFirst { case (k, ps) if k == name => ps }

  def lookupKey(st: State, relName: String, key: Value): Option[Value] =
    relationPairs(st, relName).flatMap: pairs =>
      pairs.collectFirst { case (k, v) if k == key => v }

  def lookupField(st: State, entityId: String, fieldName: String): Option[Value] =
    st.entityFields.collectFirst { case (k, fs) if k == entityId => fs }
      .flatMap(fs => fs.collectFirst { case (f, v) if f == fieldName => v })

  def asBool(v: Value): Option[Boolean] = v match
    case Value.VBool(b) => Some(b)
    case _              => None

  def asInt(v: Value): Option[BigInt] = v match
    case Value.VInt(n) => Some(n)
    case _             => None

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
    case BinOp.Eq  => Some(l == r)
    case BinOp.Neq => Some(l != r)
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

  def eval(s: Schema, st: State, env: Env, expr: Expr): Option[Value] = expr match
    case Expr.BoolLit(v, _) => Some(Value.VBool(v))
    case Expr.IntLit(v, _)  => Some(Value.VInt(BigInt(v)))
    case Expr.Identifier(name, _) =>
      envLookup(env, name).orElse(stateScalar(st, name))
    case Expr.UnaryOp(UnOp.Not, operand, _) =>
      eval(s, st, env, operand).flatMap(asBool).map(b => Value.VBool(!b))
    case Expr.UnaryOp(UnOp.Negate, operand, _) =>
      eval(s, st, env, operand).flatMap(asInt).map(n => Value.VInt(-n))
    case Expr.UnaryOp(UnOp.Cardinality, Expr.Identifier(relName, _), _) =>
      relationDomain(st, relName).map(dom => Value.VInt(BigInt(dom.length)))
    case Expr.BinaryOp(op, l, r, _) if isBoolBinOp(op) =>
      for
        lv  <- eval(s, st, env, l)
        rv  <- eval(s, st, env, r)
        lb  <- asBool(lv)
        rb  <- asBool(rv)
        out <- evalBoolBin(op, lb, rb)
      yield Value.VBool(out)
    case Expr.BinaryOp(op, l, r, _) if isArithOp(op) =>
      for
        lv  <- eval(s, st, env, l)
        rv  <- eval(s, st, env, r)
        out <- evalArith(op, lv, rv)
      yield out
    case Expr.BinaryOp(op, l, r, _) if isCmpOp(op) =>
      for
        lv  <- eval(s, st, env, l)
        rv  <- eval(s, st, env, r)
        out <- evalCmp(op, lv, rv)
      yield Value.VBool(out)
    case Expr.BinaryOp(BinOp.In, elem, Expr.Identifier(relName, _), _) =>
      for
        v   <- eval(s, st, env, elem)
        dom <- relationDomain(st, relName)
      yield Value.VBool(dom.contains(v))
    case Expr.BinaryOp(BinOp.NotIn, elem, rel @ Expr.Identifier(_, _), _) =>
      eval(s, st, env, Expr.UnaryOp(UnOp.Not, Expr.BinaryOp(BinOp.In, elem, rel)))
    case Expr.BinaryOp(
          BinOp.Subset,
          Expr.Identifier(r1, _),
          Expr.Identifier(r2, _),
          _
        ) =>
      // Subset(r1, r2)  ≡  ∀ x ∈ r1, x ∈ r2. Mirror the Lean-side `forallRel + member`
      // composition that Emit renders.
      for
        dom1 <- relationDomain(st, r1)
        dom2 <- relationDomain(st, r2)
      yield Value.VBool(dom1.forall(dom2.contains))
    case Expr.Index(Expr.Identifier(relName, _), keyExpr, _) =>
      eval(s, st, env, keyExpr).flatMap(kv => lookupKey(st, relName, kv))
    case Expr.FieldAccess(base, fieldName, _) =>
      // M_L.4.k: evaluate base to a `vEntity _ id`, then look up the field by id.
      // Bare-Identifier `state_scalar.field` (M_L.4.h) is the special case where
      // the scalar's value is `vEntity name id` from demo-state seeding.
      eval(s, st, env, base).flatMap:
        case Value.VEntity(_, id) => lookupField(st, id, fieldName)
        case _                    => None
    case Expr.Let(name, value, body, _) =>
      eval(s, st, env, value).flatMap(v => eval(s, st, (name, v) :: env, body))
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
              evalForallEnum(s, st, env, v, name, members, body)
            case None =>
              relationDomain(st, name) match
                case Some(dom) => evalForallRel(s, st, env, v, dom, body)
                case None      => None
        case _ => None
    case Expr.Quantifier(QuantKind.No, bindings, body, _) =>
      // No x, P  ≡  ∀ x, ¬ P. Reduce to the existing All-arm so EvalIR matches Lean.
      eval(
        s,
        st,
        env,
        Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
      )
    case Expr.Quantifier(QuantKind.Some, bindings, body, _) =>
      // ∃ x, P  ≡  ¬ ∀ x, ¬ P
      eval(
        s,
        st,
        env,
        Expr.UnaryOp(
          UnOp.Not,
          Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
        )
      )
    case Expr.Quantifier(QuantKind.Exists, bindings, body, _) =>
      // Alias of Some.
      eval(
        s,
        st,
        env,
        Expr.UnaryOp(
          UnOp.Not,
          Expr.Quantifier(QuantKind.All, bindings, Expr.UnaryOp(UnOp.Not, body))
        )
      )
    case Expr.Prime(inner, _) => eval(s, st, env, inner)
    case Expr.Pre(inner, _)   => eval(s, st, env, inner)
    case _                    => None

  private def isBoolBinOp(op: BinOp): Boolean = op match
    case BinOp.And | BinOp.Or | BinOp.Implies | BinOp.Iff => true
    case _                                                => false

  private def isArithOp(op: BinOp): Boolean = op match
    case BinOp.Add | BinOp.Sub | BinOp.Mul | BinOp.Div => true
    case _                                             => false

  private def isCmpOp(op: BinOp): Boolean = op match
    case BinOp.Eq | BinOp.Neq | BinOp.Lt | BinOp.Le | BinOp.Gt | BinOp.Ge => true
    case _                                                                => false

  private def evalForallEnum(
      s: Schema,
      st: State,
      env: Env,
      v: String,
      enName: String,
      members: List[String],
      body: Expr
  ): Option[Value] =
    val results = members.map: m =>
      val extEnv = (v, Value.VEnum(enName, m)) :: env
      eval(s, st, extEnv, body).flatMap(asBool)
    if results.exists(_.isEmpty) then None
    else Some(Value.VBool(results.flatten.forall(identity)))

  private def evalForallRel(
      s: Schema,
      st: State,
      env: Env,
      v: String,
      dom: List[Value],
      body: Expr
  ): Option[Value] =
    val results = dom.map: value =>
      val extEnv = (v, value) :: env
      eval(s, st, extEnv, body).flatMap(asBool)
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
