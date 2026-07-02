package specrest.verify.z3

import specrest.ir.generated.SpecRestGenerated.*

enum Z3Sort derives CanEqual:
  case Int
  case Real
  case Bool
  case Uninterp(name: String)
  case SetOf(elem: Z3Sort)
  case OptionOf(elem: Z3Sort)
  case SeqOf(elem: Z3Sort)
  case MapOf(key: Z3Sort, value: Z3Sort)
  case Str

object Z3Sort:
  val IntS: Z3Sort  = Int
  val BoolS: Z3Sort = Bool

  val numeric: Set[Z3Sort] = Set(Int, Real)

  def isNumeric(s: Z3Sort): Boolean = numeric.contains(s)

  def key(s: Z3Sort): String = s match
    case Uninterp(n) => s"U:$n"
    case Int         => "Int"
    case Real        => "Real"
    case Bool        => "Bool"
    case SetOf(e)    => s"Set(${key(e)})"
    case OptionOf(e) => s"Option(${key(e)})"
    case SeqOf(e)    => s"Seq(${key(e)})"
    case MapOf(k, v) => s"Map(${key(k)},${key(v)})"
    case Str         => "Str"

  def eq(a: Z3Sort, b: Z3Sort): Boolean = key(a) == key(b)

final case class Z3FunctionDecl(
    name: String,
    argSorts: List[Z3Sort],
    resultSort: Z3Sort
)

final case class Z3Binding(name: String, sort: Z3Sort)

enum CmpOp derives CanEqual:
  case Eq, Neq, Lt, Le, Gt, Ge

object CmpOp:
  def token(op: CmpOp): String = op match
    case Eq  => "="
    case Neq => "!="
    case Lt  => "<"
    case Le  => "<="
    case Gt  => ">"
    case Ge  => ">="

enum ArithOp derives CanEqual:
  case Add, Sub, Mul, Div

object ArithOp:
  def token(op: ArithOp): String = op match
    case Add => "+"
    case Sub => "-"
    case Mul => "*"
    case Div => "/"

enum QKind derives CanEqual:
  case ForAll, Exists

enum SetOpKind derives CanEqual:
  case Union, Intersect, Diff, Subset

object SetOpKind:
  def token(op: SetOpKind): String = op match
    case Union     => "union"
    case Intersect => "intersection"
    case Diff      => "setminus"
    case Subset    => "subset"

enum Z3Expr derives CanEqual:
  case Var(name: String, sort: Z3Sort, span: Option[span_t] = None)
  case App(func: String, args: List[Z3Expr], span: Option[span_t] = None)
  case IntLit(value: BigInt, span: Option[span_t] = None)
  case RealLit(num: BigInt, den: BigInt, span: Option[span_t] = None)
  case BoolLit(value: Boolean, span: Option[span_t] = None)
  case And(args: List[Z3Expr], span: Option[span_t] = None)
  case Or(args: List[Z3Expr], span: Option[span_t] = None)
  case Not(arg: Z3Expr, span: Option[span_t] = None)
  case Implies(lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case Cmp(op: CmpOp, lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case StrCmp(op: CmpOp, lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case StrConcat(lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case SeqConcat(lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case SeqContains(seq: Z3Expr, elem: Z3Expr, span: Option[span_t] = None)
  case Arith(op: ArithOp, args: List[Z3Expr], span: Option[span_t] = None)
  case Quantifier(
      q: QKind,
      bindings: List[Z3Binding],
      body: Z3Expr,
      span: Option[span_t] = None
  )
  case EmptySet(elemSort: Z3Sort, span: Option[span_t] = None)
  case SetLit(elemSort: Z3Sort, members: List[Z3Expr], span: Option[span_t] = None)
  case SetMember(elem: Z3Expr, set: Z3Expr, span: Option[span_t] = None)
  case SetBinOp(op: SetOpKind, lhs: Z3Expr, rhs: Z3Expr, span: Option[span_t] = None)
  case Ite(cond: Z3Expr, thenE: Z3Expr, elseE: Z3Expr, span: Option[span_t] = None)
  case OptNone(elemSort: Z3Sort, span: Option[span_t] = None)
  case OptSome(value: Z3Expr, span: Option[span_t] = None)
  case OptGet(value: Z3Expr, span: Option[span_t] = None)
  case StrLit(value: String, span: Option[span_t] = None)
  case InRe(str: Z3Expr, re: Z3Regex, span: Option[span_t] = None)
  case SeqLit(elemSort: Z3Sort, members: List[Z3Expr], span: Option[span_t] = None)
  case MapLit(
      keySort: Z3Sort,
      valueSort: Z3Sort,
      entries: List[(Z3Expr, Z3Expr)],
      span: Option[span_t] = None
  )

  // Exhaustive on purpose (no wildcard): a new constructor must decide its
  // child list here, or trigger inference and substitution silently treat it
  // as a leaf.
  def children: List[Z3Expr] = this match
    case Var(_, _, _)           => Nil
    case App(_, args, _)        => args
    case IntLit(_, _)           => Nil
    case RealLit(_, _, _)       => Nil
    case BoolLit(_, _)          => Nil
    case And(args, _)           => args
    case Or(args, _)            => args
    case Not(a, _)              => List(a)
    case Implies(l, r, _)       => List(l, r)
    case Cmp(_, l, r, _)        => List(l, r)
    case StrCmp(_, l, r, _)     => List(l, r)
    case StrConcat(l, r, _)     => List(l, r)
    case SeqConcat(l, r, _)     => List(l, r)
    case SeqContains(s, e, _)   => List(s, e)
    case Arith(_, args, _)      => args
    case Quantifier(_, _, b, _) => List(b)
    case EmptySet(_, _)         => Nil
    case SetLit(_, ms, _)       => ms
    case SetMember(el, s, _)    => List(el, s)
    case SetBinOp(_, l, r, _)   => List(l, r)
    case Ite(c, t, el, _)       => List(c, t, el)
    case OptNone(_, _)          => Nil
    case OptSome(v, _)          => List(v)
    case OptGet(v, _)           => List(v)
    case StrLit(_, _)           => Nil
    case InRe(s, _, _)          => List(s)
    case SeqLit(_, ms, _)       => ms
    case MapLit(_, _, es, _)    => es.flatMap((k, v) => List(k, v))

  def mapChildren(f: Z3Expr => Z3Expr): Z3Expr = this match
    case e: Var                         => e
    case App(fn, args, sp)              => App(fn, args.map(f), sp)
    case e: IntLit                      => e
    case e: RealLit                     => e
    case e: BoolLit                     => e
    case And(args, sp)                  => And(args.map(f), sp)
    case Or(args, sp)                   => Or(args.map(f), sp)
    case Not(a, sp)                     => Not(f(a), sp)
    case Implies(l, r, sp)              => Implies(f(l), f(r), sp)
    case Cmp(op, l, r, sp)              => Cmp(op, f(l), f(r), sp)
    case StrCmp(op, l, r, sp)           => StrCmp(op, f(l), f(r), sp)
    case StrConcat(l, r, sp)            => StrConcat(f(l), f(r), sp)
    case SeqConcat(l, r, sp)            => SeqConcat(f(l), f(r), sp)
    case SeqContains(s, e, sp)          => SeqContains(f(s), f(e), sp)
    case Arith(op, args, sp)            => Arith(op, args.map(f), sp)
    case Quantifier(q, bindings, b, sp) => Quantifier(q, bindings, f(b), sp)
    case e: EmptySet                    => e
    case SetLit(es, ms, sp)             => SetLit(es, ms.map(f), sp)
    case SetMember(el, s, sp)           => SetMember(f(el), f(s), sp)
    case SetBinOp(op, l, r, sp)         => SetBinOp(op, f(l), f(r), sp)
    case Ite(c, t, el, sp)              => Ite(f(c), f(t), f(el), sp)
    case e: OptNone                     => e
    case OptSome(v, sp)                 => OptSome(f(v), sp)
    case OptGet(v, sp)                  => OptGet(f(v), sp)
    case e: StrLit                      => e
    case InRe(s, re, sp)                => InRe(f(s), re, sp)
    case SeqLit(es, ms, sp)             => SeqLit(es, ms.map(f), sp)
    case MapLit(ks, vs, es, sp)         => MapLit(ks, vs, es.map((k, v) => (f(k), f(v))), sp)

  def freeVars: Set[String] = this match
    case Var(n, _, _)               => Set(n)
    case Quantifier(_, bs, body, _) => body.freeVars -- bs.map(_.name)
    case other                      => other.children.foldLeft(Set.empty[String])(_ ++ _.freeVars)

  def substitute(varName: String, replacement: Z3Expr): Z3Expr = this match
    case Var(n, _, _) if n == varName                                            => replacement
    case q @ Quantifier(_, bindings, _, _) if bindings.exists(_.name == varName) => q
    case Quantifier(k, bindings, body, sp)
        if body.freeVars(varName) && bindings.exists(b => replacement.freeVars(b.name)) =>
      // Capture avoidance: a binder that also occurs free in the replacement is
      // renamed apart before substituting under it. Only when the substituted
      // name is actually free in the body; otherwise the plain descent below is
      // already capture-safe and renaming would churn binder names for nothing.
      val replFree     = replacement.freeVars
      val initialTaken = replFree ++ body.freeVars ++ bindings.map(_.name) + varName
      val (renamedBindings, renamedBody, _) =
        bindings.foldLeft((List.empty[Z3Binding], body, initialTaken)):
          case ((bs, bd, taken), b) =>
            if replFree(b.name) then
              val fresh = freshName(b.name, taken)
              (
                bs :+ Z3Binding(fresh, b.sort),
                bd.substitute(b.name, Var(fresh, b.sort)),
                taken + fresh
              )
            else (bs :+ b, bd, taken)
      Quantifier(k, renamedBindings, renamedBody.substitute(varName, replacement), sp)
    case other => other.mapChildren(_.substitute(varName, replacement))

  private def freshName(base: String, taken: Set[String]): String =
    LazyList.from(1).map(i => s"${base}_$i").find(n => !taken(n)).getOrElse(base)

  def spanOpt: Option[span_t] = this match
    case e: Var         => e.span
    case e: App         => e.span
    case e: IntLit      => e.span
    case e: RealLit     => e.span
    case e: BoolLit     => e.span
    case e: And         => e.span
    case e: Or          => e.span
    case e: Not         => e.span
    case e: Implies     => e.span
    case e: Cmp         => e.span
    case e: StrCmp      => e.span
    case e: StrConcat   => e.span
    case e: SeqConcat   => e.span
    case e: SeqContains => e.span
    case e: Arith       => e.span
    case e: Quantifier  => e.span
    case e: EmptySet    => e.span
    case e: SetLit      => e.span
    case e: SetMember   => e.span
    case e: SetBinOp    => e.span
    case e: Ite         => e.span
    case e: OptNone     => e.span
    case e: OptSome     => e.span
    case e: OptGet      => e.span
    case e: StrLit      => e.span
    case e: InRe        => e.span
    case e: SeqLit      => e.span
    case e: MapLit      => e.span

  def withSpan(s: Option[span_t]): Z3Expr =
    if s.isEmpty then this
    else
      this match
        case e: Var         => e.copy(span = s)
        case e: App         => e.copy(span = s)
        case e: IntLit      => e.copy(span = s)
        case e: RealLit     => e.copy(span = s)
        case e: BoolLit     => e.copy(span = s)
        case e: And         => e.copy(span = s)
        case e: Or          => e.copy(span = s)
        case e: Not         => e.copy(span = s)
        case e: Implies     => e.copy(span = s)
        case e: Cmp         => e.copy(span = s)
        case e: StrCmp      => e.copy(span = s)
        case e: StrConcat   => e.copy(span = s)
        case e: SeqConcat   => e.copy(span = s)
        case e: SeqContains => e.copy(span = s)
        case e: Arith       => e.copy(span = s)
        case e: Quantifier  => e.copy(span = s)
        case e: EmptySet    => e.copy(span = s)
        case e: SetLit      => e.copy(span = s)
        case e: SetMember   => e.copy(span = s)
        case e: SetBinOp    => e.copy(span = s)
        case e: Ite         => e.copy(span = s)
        case e: OptNone     => e.copy(span = s)
        case e: OptSome     => e.copy(span = s)
        case e: OptGet      => e.copy(span = s)
        case e: StrLit      => e.copy(span = s)
        case e: InRe        => e.copy(span = s)
        case e: SeqLit      => e.copy(span = s)
        case e: MapLit      => e.copy(span = s)

enum Z3Regex derives CanEqual:
  case Str(s: String)
  case Range(lo: Char, hi: Char)
  case AnyChar
  case Union(res: List[Z3Regex])
  case Concat(res: List[Z3Regex])
  case Star(re: Z3Regex)
  case Plus(re: Z3Regex)
  case Opt(re: Z3Regex)
  case Comp(re: Z3Regex)
  case Inter(res: List[Z3Regex])

final case class ArtifactEntityField(name: String, sort: Z3Sort, funcName: String)
final case class ArtifactEntity(name: String, sort: Z3Sort, fields: List[ArtifactEntityField])
final case class ArtifactEnumMember(name: String, funcName: String)
final case class ArtifactEnum(name: String, sort: Z3Sort, members: List[ArtifactEnumMember])

enum ArtifactStateEntry derives CanEqual:
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
