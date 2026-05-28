package specrest.verify

import specrest.ir.generated.SpecRestGenerated
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full
import specrest.ir.generated.SpecRestGenerated.trust_level

enum TrustLevel derives CanEqual:
  case Sound, BestEffort

object TrustLevel:
  def token(t: TrustLevel): String = t match
    case Sound      => "sound"
    case BestEffort => "best-effort"

  private[verify] def fromLifted(t: trust_level): TrustLevel = t match
    case _: SpecRestGenerated.TlSound      => Sound
    case _: SpecRestGenerated.TlBestEffort => BestEffort

object Trust:

  def enumNames(ir: ServiceIRFull): List[String] =
    SpecRestGenerated.verifyEnumNames(ir)

  def classify(enums: List[String], exprs: List[expr_full]): TrustLevel =
    TrustLevel.fromLifted(SpecRestGenerated.foldTrust(enums, exprs))

  def classify(enums: List[String], e: expr_full): TrustLevel =
    classify(enums, List(e))
