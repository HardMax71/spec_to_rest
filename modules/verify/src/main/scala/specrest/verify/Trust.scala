package specrest.verify

import specrest.ir.generated.SpecRestGenerated.EnumDeclFull
import specrest.ir.generated.SpecRestGenerated.ServiceIRFull
import specrest.ir.generated.SpecRestGenerated.expr_full
import specrest.ir.generated.SpecRestGenerated.lower

enum TrustLevel derives CanEqual:
  case Sound, BestEffort

object TrustLevel:
  def token(t: TrustLevel): String = t match
    case Sound      => "sound"
    case BestEffort => "best-effort"

object Trust:

  def enumNames(ir: ServiceIRFull): List[String] =
    ir.d.collect { case EnumDeclFull(n, _, _) => n }

  def classify(enums: List[String], exprs: List[expr_full]): TrustLevel =
    if exprs.forall(e => lower(enums, e).isDefined) then TrustLevel.Sound
    else TrustLevel.BestEffort

  def classify(enums: List[String], e: expr_full): TrustLevel =
    if lower(enums, e).isDefined then TrustLevel.Sound else TrustLevel.BestEffort
