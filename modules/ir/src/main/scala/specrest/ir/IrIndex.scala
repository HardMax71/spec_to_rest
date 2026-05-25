package specrest.ir

import specrest.ir.generated.SpecRestGenerated.*

final case class IrIndex(
    entities: List[EntityDeclFull],
    enums: List[EnumDeclFull],
    aliases: List[TypeAliasDeclFull],
    entityByName: Map[String, EntityDeclFull],
    enumByName: Map[String, EnumDeclFull],
    aliasByName: Map[String, TypeAliasDeclFull],
    entityNames: Set[String],
    enumNames: Set[String],
    aliasNames: Set[String]
):
  def aliasAList: List[(String, TypeAliasDeclFull)] = aliasByName.toList
  def enumAList: List[(String, EnumDeclFull)]       = enumByName.toList

object IrIndex:
  private val cache: java.util.Map[ServiceIRFull, IrIndex] =
    java.util.Collections.synchronizedMap(new java.util.WeakHashMap[ServiceIRFull, IrIndex]())

  def of(ir: ServiceIRFull): IrIndex =
    Option(cache.get(ir)) match
      case Some(hit) => hit
      case None =>
        val fresh = build(ir)
        cache.put(ir, fresh)
        fresh

  private def build(ir: ServiceIRFull): IrIndex =
    val entities = ir.c.collect { case e: EntityDeclFull => e }
    val enums    = ir.d.collect { case e: EnumDeclFull => e }
    val aliases  = ir.e.collect { case a: TypeAliasDeclFull => a }
    IrIndex(
      entities = entities,
      enums = enums,
      aliases = aliases,
      entityByName = entities.map(e => e.a -> e).toMap,
      enumByName = enums.map(e => e.a -> e).toMap,
      aliasByName = aliases.map(a => a.a -> a).toMap,
      entityNames = entities.map(_.a).toSet,
      enumNames = enums.map(_.a).toSet,
      aliasNames = aliases.map(_.a).toSet
    )

extension (ir: ServiceIRFull) def idx: IrIndex = IrIndex.of(ir)
