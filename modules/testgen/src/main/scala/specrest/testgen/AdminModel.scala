package specrest.testgen

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

  def unbackedStateFieldNames(ir: ServiceIRFull): Set[String] =
    ir.f.toList.flatMap {
      case StateDeclFull(fs, _) => fs.collect { case f: StateFieldDeclFull => f }
    }.filter(f => projectionFor(f, ir).isEmpty).map(_.a).toSet

  def projectionFor(f: StateFieldDeclFull, ir: ServiceIRFull): Option[Projection] =
    f.b match
      case RelationTypeF(k, _, v, _) =>
        inferRelationProjection(k, v, ir)
      case NamedTypeF(name, _) =>
        ir.c.collect { case e: EntityDeclFull if e.a == name => e }.headOption
          .flatMap(e =>
            primaryKeyField(e).map(pk => Projection(name, pk, ProjectionValue.EntityRow))
          )
      case _ => None

  private def inferRelationProjection(
      k: type_expr_full,
      v: type_expr_full,
      ir: ServiceIRFull
  ): Option[Projection] =
    val kName      = typeName(k)
    val vName      = typeName(v)
    val entityList = ir.c.collect { case e: EntityDeclFull => e }
    (kName, vName) match
      case (Some(kn), Some(vn)) if entityList.exists(_.a == vn) =>
        for
          entity <- entityList.find(_.a == vn)
          keyField <-
            entity.c.collect { case f: FieldDeclFull => f }.find(f => typeName(f.b).contains(kn))
        yield Projection(vn, keyField.a, ProjectionValue.EntityRow)
      case (Some(kn), Some(vn)) =>
        entityList
          .find: e =>
            val ef = e.c.collect { case f: FieldDeclFull => f }
            ef.exists(f => typeName(f.b).contains(kn)) &&
            ef.exists(f => typeName(f.b).contains(vn))
          .flatMap: e =>
            val ef = e.c.collect { case f: FieldDeclFull => f }
            for
              keyField <- ef.find(f => typeName(f.b).contains(kn))
              valField <- ef.find(f =>
                            typeName(f.b).contains(vn) && f.a != keyField.a
                          )
            yield Projection(e.a, keyField.a, ProjectionValue.PrimitiveField(valField.a))
      case _ => None

  def primaryKeyField(e: EntityDeclFull): Option[String] =
    val fs = e.c.collect { case f: FieldDeclFull => f }
    fs.find(_.a == "id").map(_.a).orElse(fs.headOption.map(_.a))

  def isDateTimeType(
      t: type_expr_full,
      ir: ServiceIRFull,
      seen: Set[String]
  ): Boolean =
    t match
      case NamedTypeF("DateTime", _) => true
      case OptionTypeF(inner, _)     => isDateTimeType(inner, ir, seen)
      case NamedTypeF(name, _) if !seen.contains(name) =>
        ir.e.collect { case a: TypeAliasDeclFull => a }
          .find(_.a == name)
          .exists(alias => isDateTimeType(alias.b, ir, seen + name))
      case _ => false
