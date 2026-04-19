package specrest.verify

final case class DecodedValue(display: String, entityLabel: Option[String])

final case class DecodedEntityField(name: String, value: DecodedValue)

final case class DecodedEntity(
    sortName: String,
    label: String,
    rawElement: String,
    fields: List[DecodedEntityField]
)

final case class DecodedRelationEntry(key: DecodedValue, value: DecodedValue)

final case class DecodedRelation(
    stateName: String,
    side: String,
    entries: List[DecodedRelationEntry]
)

final case class DecodedConstant(
    stateName: String,
    side: String,
    value: DecodedValue
)

final case class DecodedInput(name: String, value: DecodedValue)

final case class DecodedCounterExample(
    entities: List[DecodedEntity],
    stateRelations: List[DecodedRelation],
    stateConstants: List[DecodedConstant],
    inputs: List[DecodedInput]
)

object CounterExample:

  def format(ce: DecodedCounterExample): String =
    val lines = List.newBuilder[String]
    if ce.inputs.nonEmpty then
      lines += "  inputs:"
      for inp <- ce.inputs do lines += s"    ${inp.name} = ${inp.value.display}"
    if ce.entities.nonEmpty then
      lines += "  entities:"
      for e <- ce.entities do
        val fieldStr = e.fields.map(f => s"${f.name} = ${f.value.display}").mkString(", ")
        lines += s"    ${e.label} { $fieldStr }"
    val pre  = ce.stateRelations.filter(_.side == "pre")
    val preC = ce.stateConstants.filter(_.side == "pre")
    if pre.nonEmpty || preC.nonEmpty then
      lines += "  pre-state:"
      for r <- pre do lines += formatRelation(r)
      for c <- preC do lines += s"    ${c.stateName} = ${c.value.display}"
    val post  = ce.stateRelations.filter(_.side == "post")
    val postC = ce.stateConstants.filter(_.side == "post")
    if post.nonEmpty || postC.nonEmpty then
      lines += "  post-state:"
      for r <- post do lines += formatRelation(r)
      for c <- postC do lines += s"    ${c.stateName}' = ${c.value.display}"
    lines.result().mkString("\n")

  private def formatRelation(r: DecodedRelation): String =
    val label = if r.side == "pre" then r.stateName else s"${r.stateName}'"
    if r.entries.isEmpty then s"    $label = {}"
    else
      val entryStrs = r.entries.map(e => s"${e.key.display} → ${e.value.display}").mkString(", ")
      s"    $label = { $entryStrs }"
