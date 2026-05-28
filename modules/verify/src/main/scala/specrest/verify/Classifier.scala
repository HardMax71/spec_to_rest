package specrest.verify

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.*

enum VerifierTool derives CanEqual:
  case Z3, Alloy

object VerifierTool:
  def token(t: VerifierTool): String = t match
    case Z3    => "z3"
    case Alloy => "alloy"

  private[verify] def fromLifted(v: verifier_tool): VerifierTool = v match
    case _: VtZ3    => Z3
    case _: VtAlloy => Alloy

object Classifier:

  def classifyGlobal(ir: ServiceIRFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyGlobalVerifier(ir))

  def classifyInvariant(inv: InvariantDeclFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyInvariantVerifier(inv))

  def classifyRequires(op: OperationDeclFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyRequiresVerifier(op))

  def classifyEnabled(op: OperationDeclFull, ir: ServiceIRFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyEnabledVerifier(op, ir))

  def classifyPreservation(op: OperationDeclFull, inv: InvariantDeclFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyPreservationVerifier(op, inv))

  def classifyTemporal(@annotation.unused t: TemporalDeclFull): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyTemporalVerifier)
