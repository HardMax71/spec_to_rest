package specrest.ir

final case class Span(
    startLine: Int,
    startCol: Int,
    endLine: Int,
    endCol: Int,
)

enum Multiplicity:
  case One, Lone, Some, Set

enum BinOp:
  case And, Or, Implies, Iff
  case Eq, Neq
  case Lt, Gt, Le, Ge
  case In, NotIn
  case Subset, Union, Intersect, Diff
  case Add, Sub, Mul, Div

enum UnOp:
  case Not, Negate, Cardinality, Power

enum QuantKind:
  case All, Some, No, Exists

enum BindingKind:
  case In, Colon

enum TypeExpr:
  case NamedType(name: String, span: Option[Span] = None)
  case SetType(elementType: TypeExpr, span: Option[Span] = None)
  case MapType(keyType: TypeExpr, valueType: TypeExpr, span: Option[Span] = None)
  case SeqType(elementType: TypeExpr, span: Option[Span] = None)
  case OptionType(innerType: TypeExpr, span: Option[Span] = None)
  case RelationType(
      fromType: TypeExpr,
      multiplicity: Multiplicity,
      toType: TypeExpr,
      span: Option[Span] = None,
  )

final case class FieldAssign(name: String, value: Expr, span: Option[Span] = None)
final case class MapEntry(key: Expr, value: Expr, span: Option[Span] = None)
final case class QuantifierBinding(
    variable: String,
    domain: Expr,
    bindingKind: BindingKind,
    span: Option[Span] = None,
)

enum Expr:
  case BinaryOp(op: BinOp, left: Expr, right: Expr, span: Option[Span] = None)
  case UnaryOp(op: UnOp, operand: Expr, span: Option[Span] = None)
  case Quantifier(
      quantifier: QuantKind,
      bindings: List[QuantifierBinding],
      body: Expr,
      span: Option[Span] = None,
  )
  case SomeWrap(expr: Expr, span: Option[Span] = None)
  case The(variable: String, domain: Expr, body: Expr, span: Option[Span] = None)
  case FieldAccess(base: Expr, field: String, span: Option[Span] = None)
  case EnumAccess(base: Expr, member: String, span: Option[Span] = None)
  case Index(base: Expr, index: Expr, span: Option[Span] = None)
  case Call(callee: Expr, args: List[Expr], span: Option[Span] = None)
  case Prime(expr: Expr, span: Option[Span] = None)
  case Pre(expr: Expr, span: Option[Span] = None)
  case With(base: Expr, updates: List[FieldAssign], span: Option[Span] = None)
  case If(condition: Expr, thenBranch: Expr, elseBranch: Expr, span: Option[Span] = None)
  case Let(variable: String, value: Expr, body: Expr, span: Option[Span] = None)
  case Lambda(param: String, body: Expr, span: Option[Span] = None)
  case Constructor(
      typeName: String,
      fields: List[FieldAssign],
      span: Option[Span] = None,
  )
  case SetLiteral(elements: List[Expr], span: Option[Span] = None)
  case MapLiteral(entries: List[MapEntry], span: Option[Span] = None)
  case SetComprehension(
      variable: String,
      domain: Expr,
      predicate: Expr,
      span: Option[Span] = None,
  )
  case SeqLiteral(elements: List[Expr], span: Option[Span] = None)
  case Matches(expr: Expr, pattern: String, span: Option[Span] = None)
  case IntLit(value: Long, span: Option[Span] = None)
  case FloatLit(value: Double, span: Option[Span] = None)
  case StringLit(value: String, span: Option[Span] = None)
  case BoolLit(value: Boolean, span: Option[Span] = None)
  case NoneLit(span: Option[Span] = None)
  case Identifier(name: String, span: Option[Span] = None)

final case class FieldDecl(
    name: String,
    typeExpr: TypeExpr,
    constraint: Option[Expr] = None,
    span: Option[Span] = None,
)

final case class EntityDecl(
    name: String,
    extends_ : Option[String] = None,
    fields: List[FieldDecl] = Nil,
    invariants: List[Expr] = Nil,
    span: Option[Span] = None,
)

final case class EnumDecl(
    name: String,
    values: List[String],
    span: Option[Span] = None,
)

final case class TypeAliasDecl(
    name: String,
    typeExpr: TypeExpr,
    constraint: Option[Expr] = None,
    span: Option[Span] = None,
)

final case class StateFieldDecl(
    name: String,
    typeExpr: TypeExpr,
    span: Option[Span] = None,
)

final case class StateDecl(
    fields: List[StateFieldDecl],
    span: Option[Span] = None,
)

final case class ParamDecl(
    name: String,
    typeExpr: TypeExpr,
    span: Option[Span] = None,
)

final case class OperationDecl(
    name: String,
    inputs: List[ParamDecl] = Nil,
    outputs: List[ParamDecl] = Nil,
    requires: List[Expr] = Nil,
    ensures: List[Expr] = Nil,
    span: Option[Span] = None,
)

final case class TransitionRule(
    from: String,
    to: String,
    via: String,
    guard: Option[Expr] = None,
    span: Option[Span] = None,
)

final case class TransitionDecl(
    name: String,
    entityName: String,
    fieldName: String,
    rules: List[TransitionRule] = Nil,
    span: Option[Span] = None,
)

final case class InvariantDecl(
    name: Option[String],
    expr: Expr,
    span: Option[Span] = None,
)

final case class FactDecl(
    name: Option[String],
    expr: Expr,
    span: Option[Span] = None,
)

final case class FunctionDecl(
    name: String,
    params: List[ParamDecl],
    returnType: TypeExpr,
    body: Expr,
    span: Option[Span] = None,
)

final case class PredicateDecl(
    name: String,
    params: List[ParamDecl],
    body: Expr,
    span: Option[Span] = None,
)

final case class ConventionRule(
    target: String,
    property: String,
    qualifier: Option[String],
    value: Expr,
    span: Option[Span] = None,
)

final case class ConventionsDecl(
    rules: List[ConventionRule],
    span: Option[Span] = None,
)

final case class ServiceIR(
    name: String,
    imports: List[String] = Nil,
    entities: List[EntityDecl] = Nil,
    enums: List[EnumDecl] = Nil,
    typeAliases: List[TypeAliasDecl] = Nil,
    state: Option[StateDecl] = None,
    operations: List[OperationDecl] = Nil,
    transitions: List[TransitionDecl] = Nil,
    invariants: List[InvariantDecl] = Nil,
    facts: List[FactDecl] = Nil,
    functions: List[FunctionDecl] = Nil,
    predicates: List[PredicateDecl] = Nil,
    conventions: Option[ConventionsDecl] = None,
    span: Option[Span] = None,
)
