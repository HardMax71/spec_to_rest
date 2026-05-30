package specrest.ir

import specrest.ir.generated.SpecRestGenerated.*

final case class IrIndex(
    entities: List[entity_decl_full],
    enums: List[enum_decl_full],
    aliases: List[type_alias_decl_full],
    entityByName: Map[String, entity_decl_full],
    enumByName: Map[String, enum_decl_full],
    aliasByName: Map[String, type_alias_decl_full],
    entityNames: Set[String],
    enumNames: Set[String],
    aliasNames: Set[String]
):
  def aliasAList: List[(String, type_alias_decl_full)] = aliasByName.toList
  def enumAList: List[(String, enum_decl_full)]        = enumByName.toList
  def entityNamesList: List[String]                    = entities.map(entName)

object IrIndex:
  private val cache: java.util.Map[service_ir_full, IrIndex] =
    java.util.Collections.synchronizedMap(new java.util.WeakHashMap[service_ir_full, IrIndex]())

  def of(ir: service_ir_full): IrIndex =
    Option(cache.get(ir)) match
      case Some(hit) => hit
      case None =>
        val fresh = build(ir)
        cache.put(ir, fresh)
        fresh

  private def build(ir: service_ir_full): IrIndex =
    val entities = svcEntities(ir)
    val enums    = svcEnums(ir)
    val aliases  = svcTypeAliases(ir)
    IrIndex(
      entities = entities,
      enums = enums,
      aliases = aliases,
      entityByName = entities.map(e => entName(e) -> e).toMap,
      enumByName = enums.map(e => enmName(e) -> e).toMap,
      aliasByName = aliases.map(a => talName(a) -> a).toMap,
      entityNames = entities.map(entName).toSet,
      enumNames = enums.map(enmName).toSet,
      aliasNames = aliases.map(talName).toSet
    )

extension (ir: service_ir_full) def idx: IrIndex = IrIndex.of(ir)
