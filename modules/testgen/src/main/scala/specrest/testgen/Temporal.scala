package specrest.testgen

import specrest.ir.generated.SpecRestGenerated.*

enum TemporalShape derives CanEqual:
  case Always(arg: expr_full)
  case Eventually(arg: expr_full)
  case Fairness(arg: expr_full)
  case Unrecognized

object TemporalShape:
  def of(decl: TemporalDeclFull): TemporalShape = decl.b match
    case CallF(IdentifierF("always", _), arg :: Nil, _) =>
      TemporalShape.Always(arg)
    case CallF(IdentifierF("eventually", _), arg :: Nil, _) =>
      TemporalShape.Eventually(arg)
    case CallF(IdentifierF("fairness", _), arg :: Nil, _) =>
      TemporalShape.Fairness(arg)
    case _ =>
      TemporalShape.Unrecognized
