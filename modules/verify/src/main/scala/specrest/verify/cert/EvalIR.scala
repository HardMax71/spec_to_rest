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
      relations: List[(String, List[Value])]
  )

  object State:
    val empty: State = State(Nil, Nil)

    def demo(ir: ServiceIR): State =
      val schema = Schema.of(ir)
      val (scalarFields, relationFields) = ir.state match
        case None       => (Nil, Nil)
        case Some(decl) => decl.fields.partition(f => isScalarType(f.typeExpr))
      val scalars   = scalarFields.map(f => (f.name, defaultFor(schema, f.typeExpr)))
      val relations = relationFields.map(f => (f.name, List.empty[Value]))
      State(scalars = scalars, relations = relations)

    private def isScalarType(ty: TypeExpr): Boolean = ty match
      case _: TypeExpr.NamedType => true
      case _                     => false

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
