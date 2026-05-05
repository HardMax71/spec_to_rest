package specrest.verify

import specrest.ir.generated.SpecRestGenerated.*

import specrest.ir.*

enum VerifierTool derives CanEqual:
  case Z3, Alloy

object VerifierTool:
  def token(t: VerifierTool): String = t match
    case Z3    => "z3"
    case Alloy => "alloy"

object Classifier:

  def classifyGlobal(ir: service_ir_full): VerifierTool =
    fold(ir.invariants.map(_.expr))

  def classifyInvariant(inv: invariant_decl_full): VerifierTool =
    classify(inv.expr)

  def classifyRequires(op: operation_decl_full): VerifierTool =
    fold(op.d)

  def classifyEnabled(op: operation_decl_full, ir: service_ir_full): VerifierTool =
    fold(op.d ++ ir.invariants.map(_.expr))

  def classifyPreservation(op: operation_decl_full, inv: invariant_decl_full): VerifierTool =
    fold(inv.expr :: op.d ++ op.e)

  def classifyTemporal(@annotation.unused t: temporal_decl_full): VerifierTool =
    VerifierTool.Alloy

  private def classify(e: expr_full): VerifierTool =
    if requiresAlloy(e) then VerifierTool.Alloy else VerifierTool.Z3

  private def fold(exprs: List[expr_full]): VerifierTool =
    if exprs.exists(requiresAlloy) then VerifierTool.Alloy else VerifierTool.Z3

  private def requiresAlloy(e: expr_full): Boolean =
    containsAnywhere(e) { case UnaryOpF(UPower(), _, _) => true }

  private def containsAnywhere(e: expr_full)(pred: PartialFunction[expr_full, Boolean]): Boolean =
    pred.applyOrElse(e, (_: expr_full) => false) || childExprs(e).exists(containsAnywhere(_)(pred))

  def childExprs(e: expr_full): List[expr_full] = e match
    case BinaryOpF(_, l, r, _) => List(l, r)
    case UnaryOpF(_, a, _)     => List(a)
    case QuantifierF(_, bindings, body, _) =>
      bindings.map(_.domain) ++ List(body)
    case SomeWrapF(x, _)               => List(x)
    case TheF(_, d, b, _)              => List(d, b)
    case FieldAccessF(b, _, _)         => List(b)
    case EnumAccessF(b, _, _)          => List(b)
    case IndexF(b, i, _)               => List(b, i)
    case CallF(c, args, _)             => c :: args
    case PrimeF(x, _)                  => List(x)
    case PreF(x, _)                    => List(x)
    case WithF(b, upds, _)             => b :: upds.map(_.value)
    case IfF(c, t, el, _)              => List(c, t, el)
    case LetF(_, v, b, _)              => List(v, b)
    case LambdaF(_, b, _)              => List(b)
    case ConstructorF(_, fs, _)        => fs.map(_.value)
    case SetLiteralF(xs, _)            => xs
    case MapLiteralF(es, _)            => es.flatMap(e => List(e.key, e.value))
    case SetComprehensionF(_, d, p, _) => List(d, p)
    case SeqLiteralF(xs, _)            => xs
    case MatchesF(x, _, _)             => List(x)
    // Leaf cases — no expr_full children. Exhaustive; compiler enforces that new
    // expr_full variants with subexpressions must update this function.
    case IntLitF(_, _)     => Nil
    case FloatLitF(_, _)   => Nil
    case StringLitF(_, _)  => Nil
    case BoolLitF(_, _)    => Nil
    case NoneLitF(_)       => Nil
    case IdentifierF(_, _) => Nil
