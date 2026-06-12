package specrest.codegen

import specrest.convention.ScalarState
import specrest.ir.generated.SpecRestGenerated.*

object AdminModel:

  final case class Projection(
      entityName: String,
      keyFieldName: String,
      valueShape: ProjectionValue
  )

  enum ProjectionValue derives CanEqual:
    case PrimitiveField(fieldName: String)
    case EntityRow
    case ScalarStateColumn(columnName: String)

  def unbackedStateFieldNames(ir: ServiceIRFull): Set[String] =
    irStateFields(ir)
      .filter(f => projectionFor(f, ir).isEmpty)
      .map(stfName)
      .toSet

  def projectionFor(f: state_field_decl, ir: ServiceIRFull): Option[Projection] =
    stfType(f) match
      case RelationTypeF(k, _, v, _) =>
        inferRelationProjection(k, v, ir)
      case NamedTypeF(_, _) if ScalarState.fieldNames(ir).contains(stfName(f)) =>
        Some(
          Projection(
            "",
            "",
            ProjectionValue.ScalarStateColumn(ScalarState.columnName(stfName(f)))
          )
        )
      case NamedTypeF(name, _) =>
        svcEntities(ir)
          .find(e => entName(e) == name)
          .flatMap(e =>
            primaryKeyField(e).map(pk => Projection(name, pk, ProjectionValue.EntityRow))
          )
      case _ => None

  private def inferRelationProjection(
      k: type_expr,
      v: type_expr,
      ir: ServiceIRFull
  ): Option[Projection] =
    val kName      = typeName(k)
    val vName      = typeName(v)
    val entityList = svcEntities(ir)
    (kName, vName) match
      case (Some(kn), Some(vn)) if entityList.exists(e => entName(e) == vn) =>
        for
          entity <- entityList.find(e => entName(e) == vn)
          keyField <-
            entFields(entity).find(f => typeName(fldType(f)).contains(kn))
        yield Projection(vn, fldName(keyField), ProjectionValue.EntityRow)
      case (Some(kn), Some(vn)) =>
        entityList
          .find: e =>
            val ef = entFields(e)
            ef.exists(f => typeName(fldType(f)).contains(kn)) &&
            ef.exists(f => typeName(fldType(f)).contains(vn))
          .flatMap: e =>
            val ef = entFields(e)
            for
              keyField <- ef.find(f => typeName(fldType(f)).contains(kn))
              valField <- ef.find(f =>
                            typeName(fldType(f)).contains(vn) && fldName(f) != fldName(keyField)
                          )
            yield Projection(
              entName(e),
              fldName(keyField),
              ProjectionValue.PrimitiveField(fldName(valField))
            )
      case _ => None

  def primaryKeyField(e: entity_decl): Option[String] =
    val fs = entFields(e)
    fs.find(f => fldName(f) == "id").map(fldName).orElse(fs.headOption.map(fldName))
