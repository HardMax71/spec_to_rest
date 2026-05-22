package specrest.verify

import specrest.ir.*
import specrest.ir.generated.SpecRestGenerated.*

enum VerifierTool derives CanEqual:
  case Z3, Alloy

object VerifierTool:
  def token(t: VerifierTool): String = t match
    case Z3    => "z3"
    case Alloy => "alloy"

object Classifier:

  def classifyGlobal(ir: ServiceIRFull): VerifierTool =
    fold(ir.i.collect { case InvariantDeclFull(_, e, _) => e })

  def classifyInvariant(inv: InvariantDeclFull): VerifierTool =
    classify(inv.b)

  def classifyRequires(op: OperationDeclFull): VerifierTool =
    fold(op.d)

  def classifyEnabled(op: OperationDeclFull, ir: ServiceIRFull): VerifierTool =
    fold(op.d ++ ir.i.collect { case InvariantDeclFull(_, e, _) => e })

  def classifyPreservation(op: OperationDeclFull, inv: InvariantDeclFull): VerifierTool =
    fold(inv.b :: op.d ++ op.e)

  def classifyTemporal(@annotation.unused t: TemporalDeclFull): VerifierTool =
    VerifierTool.Alloy

  private def classify(e: expr_full): VerifierTool =
    if requiresAlloy(e) then VerifierTool.Alloy else VerifierTool.Z3

  private def fold(exprs: List[expr_full]): VerifierTool =
    if exprs.exists(requiresAlloy) then VerifierTool.Alloy else VerifierTool.Z3
