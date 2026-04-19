package specrest.verify

import specrest.ir.Span

enum Z3Sort:
  case Int
  case Bool
  case Uninterp(name: String)
  case SetOf(elem: Z3Sort)

object Z3Sort:
  val IntS: Z3Sort  = Int
  val BoolS: Z3Sort = Bool

  def key(s: Z3Sort): String = s match
    case Uninterp(n) => s"U:$n"
    case Int         => "Int"
    case Bool        => "Bool"
    case SetOf(e)    => s"Set(${key(e)})"

  def eq(a: Z3Sort, b: Z3Sort): Boolean = key(a) == key(b)

final case class Z3FunctionDecl(
    name: String,
    argSorts: List[Z3Sort],
    resultSort: Z3Sort
)

final case class Z3Binding(name: String, sort: Z3Sort)

enum CmpOp:
  case Eq, Neq, Lt, Le, Gt, Ge

object CmpOp:
  def token(op: CmpOp): String = op match
    case Eq  => "="
    case Neq => "!="
    case Lt  => "<"
    case Le  => "<="
    case Gt  => ">"
    case Ge  => ">="

enum ArithOp:
  case Add, Sub, Mul, Div

object ArithOp:
  def token(op: ArithOp): String = op match
    case Add => "+"
    case Sub => "-"
    case Mul => "*"
    case Div => "/"

enum QKind:
  case ForAll, Exists

enum SetOpKind:
  case Union, Intersect, Diff, Subset

object SetOpKind:
  def token(op: SetOpKind): String = op match
    case Union     => "union"
    case Intersect => "intersection"
    case Diff      => "setminus"
    case Subset    => "subset"

enum Z3Expr:
  case Var(name: String, sort: Z3Sort, span: Option[Span] = None)
  case App(func: String, args: List[Z3Expr], span: Option[Span] = None)
  case IntLit(value: Long, span: Option[Span] = None)
  case BoolLit(value: Boolean, span: Option[Span] = None)
  case And(args: List[Z3Expr], span: Option[Span] = None)
  case Or(args: List[Z3Expr], span: Option[Span] = None)
  case Not(arg: Z3Expr, span: Option[Span] = None)
  case Implies(lhs: Z3Expr, rhs: Z3Expr, span: Option[Span] = None)
  case Cmp(op: CmpOp, lhs: Z3Expr, rhs: Z3Expr, span: Option[Span] = None)
  case Arith(op: ArithOp, args: List[Z3Expr], span: Option[Span] = None)
  case Quantifier(
      q: QKind,
      bindings: List[Z3Binding],
      body: Z3Expr,
      span: Option[Span] = None
  )
  case EmptySet(elemSort: Z3Sort, span: Option[Span] = None)
  case SetLit(elemSort: Z3Sort, members: List[Z3Expr], span: Option[Span] = None)
  case SetMember(elem: Z3Expr, set: Z3Expr, span: Option[Span] = None)
  case SetBinOp(op: SetOpKind, lhs: Z3Expr, rhs: Z3Expr, span: Option[Span] = None)

  def withSpan(s: Option[Span]): Z3Expr =
    if s.isEmpty then this
    else
      this match
        case e: Var        => e.copy(span = s)
        case e: App        => e.copy(span = s)
        case e: IntLit     => e.copy(span = s)
        case e: BoolLit    => e.copy(span = s)
        case e: And        => e.copy(span = s)
        case e: Or         => e.copy(span = s)
        case e: Not        => e.copy(span = s)
        case e: Implies    => e.copy(span = s)
        case e: Cmp        => e.copy(span = s)
        case e: Arith      => e.copy(span = s)
        case e: Quantifier => e.copy(span = s)
        case e: EmptySet   => e.copy(span = s)
        case e: SetLit     => e.copy(span = s)
        case e: SetMember  => e.copy(span = s)
        case e: SetBinOp   => e.copy(span = s)

final case class ArtifactEntityField(name: String, sort: Z3Sort, funcName: String)
final case class ArtifactEntity(name: String, sort: Z3Sort, fields: List[ArtifactEntityField])
final case class ArtifactEnumMember(name: String, funcName: String)
final case class ArtifactEnum(name: String, sort: Z3Sort, members: List[ArtifactEnumMember])

enum ArtifactStateEntry:
  case Relation(
      name: String,
      keySort: Z3Sort,
      valueSort: Z3Sort,
      domFunc: String,
      mapFunc: String,
      domFuncPost: String,
      mapFuncPost: String
  )
  case Const(
      name: String,
      sort: Z3Sort,
      funcName: String,
      funcNamePost: String
  )

final case class ArtifactBinding(name: String, funcName: String, sort: Z3Sort)

final case class TranslatorArtifact(
    entities: List[ArtifactEntity],
    enums: List[ArtifactEnum],
    state: List[ArtifactStateEntry],
    inputs: List[ArtifactBinding],
    outputs: List[ArtifactBinding],
    hasPostState: Boolean
)

final case class Z3Script(
    sorts: List[Z3Sort],
    funcs: List[Z3FunctionDecl],
    assertions: List[Z3Expr],
    artifact: TranslatorArtifact
)
