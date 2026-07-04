package specrest.codegen

import specrest.ir.generated.SpecRestGenerated.*
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Target-neutral analysis of whether a spec's state round-trips through a
// generated bridge: which relations hydrate from which tables, which scalars
// ride the service_state singleton, and the first reason it cannot. The type
// predicates are the only target-specific part (python and go spell their
// domain types differently), so emitters inject them.
object StatePlan:

  final case class RelationPlan(
      stateField: String,
      entity: ProfiledEntity,
      keyField: Option[ProfiledField],
      valueField: Option[ProfiledField],
      isSeq: Boolean = false
  ):
    def isEntityRow: Boolean = valueField.isEmpty && !isSeq

  final case class ScalarPlan(stateField: String, columnName: String)

  final case class Plan(
      relations: List[RelationPlan],
      scalars: List[ScalarPlan]
  ):
    def entityRowRelations: List[RelationPlan] =
      relations.filter(_.isEntityRow).distinctBy(_.entity.entityName)
    def hasState: Boolean = relations.nonEmpty || scalars.nonEmpty

  def analyze(
      profiled: ProfiledService,
      fieldSupported: ProfiledField => Boolean,
      keySupported: ProfiledField => Boolean,
      seqSupported: Boolean = false
  ): Either[String, Plan] =
    val ir        = profiled.ir
    val byName    = profiled.entities.map(e => e.entityName -> e).toMap
    val relations = List.newBuilder[RelationPlan]
    val scalars   = List.newBuilder[ScalarPlan]
    val problems  = List.newBuilder[String]

    for sf <- irStateFields(ir) do
      val name = stfName(sf)
      AdminModel.projectionFor(sf, ir) match
        case None => () // unbacked: hydrates to the target's zero value, like /admin/state's null
        case Some(p) =>
          p.valueShape match
            case AdminModel.ProjectionValue.ScalarStateColumn(col) =>
              scalars += ScalarPlan(name, col)
            case shape =>
              byName.get(p.entityName) match
                case None =>
                  problems += s"state field '$name' projects onto unknown entity '${p.entityName}'"
                case Some(entity) =>
                  val unsupported = entity.fields.filterNot(fieldSupported)
                  if unsupported.nonEmpty then
                    problems += s"state field '$name': entity '${p.entityName}' has field types the bridge cannot marshal (${unsupported.map(_.domainType).distinct.mkString(", ")})"
                  else if shape == AdminModel.ProjectionValue.SeqRows then
                    if !seqSupported then
                      problems += s"state field '$name': seq-valued state is not bridgeable on this target"
                    else
                      // Ordered by the synthesized serial pk, which is not a
                      // profiled field; the bridge reinserts in seq order.
                      relations += RelationPlan(name, entity, None, None, isSeq = true)
                  else
                    entity.fields.find(_.fieldName == p.keyFieldName) match
                      case None =>
                        problems += s"state field '$name': key field '${p.keyFieldName}' not on '${p.entityName}'"
                      case Some(key) =>
                        if !keySupported(key) then
                          problems += s"state field '$name': key '${p.keyFieldName}' must be a non-optional scalar"
                        else
                          val valueField = shape match
                            case AdminModel.ProjectionValue.PrimitiveField(vf) =>
                              entity.fields.find(_.fieldName == vf)
                            case _ => None
                          shape match
                            case AdminModel.ProjectionValue.PrimitiveField(vf)
                                if valueField.isEmpty =>
                              problems += s"state field '$name': value field '$vf' not on '${p.entityName}'"
                            case _ =>
                              relations += RelationPlan(name, entity, Some(key), valueField)

    // A seq projection rewrites its entity's whole table on persist, so a
    // second projection over the same entity would be clobbered; fail closed.
    val seqEntities = relations.result().filter(_.isSeq).map(_.entity.entityName)
    for r <- relations.result() if !r.isSeq && seqEntities.contains(r.entity.entityName) do
      problems += s"state field '${r.stateField}' shares entity '${r.entity.entityName}' with a seq projection; persists would clobber each other"

    val built       = Plan(relations.result(), scalars.result())
    val persistable = built.entityRowRelations.map(_.entity.entityName).toSet
    for r <- built.relations if !r.isEntityRow && !r.isSeq do
      if !persistable.contains(r.entity.entityName) then
        problems += s"state field '${r.stateField}' projects a single column of '${r.entity.entityName}', which no entity-valued state field covers; mutations could not be written back"

    problems.result() match
      case first :: _ => Left(first)
      case Nil        => Right(built)
