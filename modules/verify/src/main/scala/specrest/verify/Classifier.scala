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

  def classifyGlobal(ir: service_ir_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyGlobalVerifier(ir))

  def classifyInvariant(inv: invariant_decl_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyInvariantVerifier(inv))

  def classifyRequires(op: operation_decl_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyRequiresVerifier(op))

  def classifyEnabled(op: operation_decl_full, ir: service_ir_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyEnabledVerifier(op, ir))

  def classifyPreservation(op: operation_decl_full, inv: invariant_decl_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyPreservationVerifier(op, inv))

  def classifyTemporal(@annotation.unused t: temporal_decl_full): VerifierTool =
    VerifierTool.fromLifted(SpecRestGenerated.classifyTemporalVerifier)
