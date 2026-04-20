package specrest.verify.alloy

import specrest.ir.Span

enum AlloyFieldMultiplicity:
  case One, Lone, Some_, Set

object AlloyFieldMultiplicity:
  def token(m: AlloyFieldMultiplicity): String = m match
    case One   => "one"
    case Lone  => "lone"
    case Some_ => "some"
    case Set   => "set"

final case class AlloySig(
    name: String,
    abstract_ : Boolean = false,
    isOne: Boolean = false,
    extends_ : Option[String] = None,
    fields: List[AlloyField] = Nil
)

final case class AlloyField(
    name: String,
    mult: AlloyFieldMultiplicity,
    elemType: String
)

final case class AlloyFact(
    name: Option[String],
    body: String,
    span: Option[Span] = None
)

final case class AlloyCommand(
    name: String,
    kind: AlloyCommandKind,
    body: String,
    scope: Int
)

enum AlloyCommandKind:
  case Run, Check

final case class AlloyModule(
    name: String,
    sigs: List[AlloySig],
    facts: List[AlloyFact],
    commands: List[AlloyCommand]
)
