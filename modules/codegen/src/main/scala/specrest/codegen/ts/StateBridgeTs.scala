package specrest.codegen.ts

import specrest.codegen.StatePlan
import specrest.ir.Naming
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates src/services/stateBridge.ts: hydrate a Dafny ServiceState from the
// database and persist its mutations back, both against Prisma's transaction
// client so kernel service functions run them inside one $transaction. The
// Dafny JS backend doubles underscores in identifiers (dtor_created__at,
// base__url) and exposes datatype fields as dtor_ properties.
object StateBridgeTs:

  private val ScalarTsTypes = Set("string", "number", "boolean", "Date")

  // Nullable fields would need Option constructors on the Dafny side; the
  // gate keeps them out until a spec actually needs them in ts.
  def plan(profiled: ProfiledService): Either[String, StatePlan.Plan] =
    StatePlan.analyze(
      profiled,
      fieldSupported = f => ScalarTsTypes.contains(f.domainType) && !f.nullable,
      keySupported = k => Set("string", "number").contains(k.domainType) && !k.nullable
    )

  private def camel(name: String): String =
    Naming.toCamelCase(name, Naming.CamelStrategy.Plain)

  private def dafnyName(specName: String): String = specName.replace("_", "__")

  private def toDafnyExpr(f: ProfiledField, rowRef: String): String =
    val access = s"$rowRef.${camel(f.fieldName)}"
    f.domainType match
      case "string" => s"stringToDafny($access)"
      case "number" => s"intToDafny($access)"
      case "Date"   => s"intToDafny(Math.floor($access.getTime() / 1000))"
      case _        => access

  private def fromDafnyExpr(f: ProfiledField, valueRef: String): String =
    val access = s"$valueRef['dtor_${dafnyName(f.fieldName)}']"
    f.domainType match
      case "string" => s"stringFromDafny($access)"
      case "number" => s"intFromDafny($access)"
      case "Date"   => s"new Date(intFromDafny($access) * 1000)"
      case _        => s"($access as boolean)"

  private def keyToDafny(key: ProfiledField, rowRef: String): String =
    key.domainType match
      case "string" => s"stringToDafny($rowRef.${camel(key.fieldName)})"
      case _        => s"intToDafny($rowRef.${camel(key.fieldName)})"

  private def keyFromDafny(key: ProfiledField): String =
    key.domainType match
      case "string" => "stringFromDafny(k)"
      case _        => "intFromDafny(k)"

  def emit(profiled: ProfiledService): String =
    val planned = plan(profiled) match
      case Right(p) => p
      case Left(_)  => StatePlan.Plan(Nil, Nil)

    val entities = planned.relations.map(_.entity).distinctBy(_.entityName)

    val hydrate = new StringBuilder
    for e <- entities do
      hydrate ++= s"  const ${camel(e.entityName)}Rows = await tx.${camel(e.entityName)}.findMany();\n"
    for r <- planned.relations do
      val rows    = s"${camel(r.entity.entityName)}Rows"
      val builder = s"${camel(r.stateField)}Map"
      hydrate ++= s"  let $builder = emptyDafnyMap() as DafnyMap;\n"
      hydrate ++= s"  for (const r of $rows) {\n"
      val value = r.valueField match
        case Some(vf) => toDafnyExpr(vf, "r")
        case None =>
          val args = r.entity.fields.map(f => s"      ${toDafnyExpr(f, "r")},").mkString("\n")
          s"dafnyModule['${r.entity.entityName}'].create_${r.entity.entityName}(\n$args\n    )"
      hydrate ++= s"    $builder = $builder.update(${keyToDafny(r.keyField, "r")}, $value);\n"
      hydrate ++= "  }\n"
      hydrate ++= s"  st['${dafnyName(r.stateField)}'] = $builder;\n"
    if planned.scalars.nonEmpty then
      hydrate ++= "  const scalarRow = await tx.serviceState.findUnique({ where: { id: 1 } });\n"
      hydrate ++= "  if (scalarRow) {\n"
      for sc <- planned.scalars do
        hydrate ++= s"    st['${dafnyName(sc.stateField)}'] = intToDafny(scalarRow.${camel(sc.columnName)});\n"
      hydrate ++= "  }\n"

    val persist = new StringBuilder
    for r <- planned.entityRowRelations do
      val e      = r.entity
      val client = camel(e.entityName)
      persist ++= s"  const ${client}Rows = await tx.$client.findMany();\n"
      persist ++= s"  const ${client}Existing = new Map(${client}Rows.map((r) => [r.${camel(r.keyField.fieldName)}, r]));\n"
      persist ++= s"  const seen = new Set<${r.keyField.domainType}>();\n"
      persist ++= s"  for (const [k, v] of st['${dafnyName(r.stateField)}'] as Iterable<[unknown, unknown]>) {\n"
      persist ++= s"    const key = ${keyFromDafny(r.keyField)};\n"
      persist ++= "    const value = v as Record<string, unknown>;\n"
      persist ++= "    seen.add(key);\n"
      val nonKey = e.fields.filter(_.fieldName != r.keyField.fieldName)
      persist ++= "    const data = {\n"
      for f <- nonKey do
        persist ++= s"      ${camel(f.fieldName)}: ${fromDafnyExpr(f, "value")},\n"
      persist ++= "    };\n"
      persist ++= s"    const row = ${client}Existing.get(key);\n"
      persist ++= "    if (row) {\n"
      persist ++= s"      await tx.$client.update({ where: { id: row.id }, data });\n"
      persist ++= "    } else {\n"
      persist ++= s"      await tx.$client.create({\n"
      persist ++= s"        data: { ${camel(r.keyField.fieldName)}: ${fromDafnyExpr(r.keyField, "value")}, ...data },\n"
      persist ++= "      });\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
      persist ++= s"  for (const [key, row] of ${client}Existing) {\n"
      persist ++= "    if (!seen.has(key)) {\n"
      persist ++= s"      await tx.$client.delete({ where: { id: row.id } });\n"
      persist ++= "    }\n"
      persist ++= "  }\n"
    if planned.scalars.nonEmpty then
      persist ++= "  const scalarData = {\n"
      for sc <- planned.scalars do
        persist ++= s"    ${camel(sc.columnName)}: intFromDafny(st['${dafnyName(sc.stateField)}']),\n"
      persist ++= "  };\n"
      persist ++= "  await tx.serviceState.upsert({\n"
      persist ++= "    where: { id: 1 },\n"
      persist ++= "    update: scalarData,\n"
      persist ++= "    create: { id: 1, ...scalarData },\n"
      persist ++= "  });\n"

    s"""import type { Prisma } from '@prisma/client';
       |
       |import {
       |  dafnyModule,
       |  emptyDafnyMap,
       |  intFromDafny,
       |  intToDafny,
       |  makeState,
       |  stringFromDafny,
       |  stringToDafny,
       |} from '../dafnyKernel/adapter.js';
       |
       |type DafnyMap = { update(k: unknown, v: unknown): DafnyMap } & Iterable<[unknown, unknown]>;
       |
       |export const hydrateState = async (tx: Prisma.TransactionClient): Promise<unknown> => {
       |  const st = makeState() as Record<string, unknown>;
       |${hydrate.toString}  return st;
       |};
       |
       |export const persistState = async (
       |  tx: Prisma.TransactionClient,
       |  state: unknown,
       |): Promise<void> => {
       |  const st = state as Record<string, unknown>;
       |${persist.toString}};
       |""".stripMargin
