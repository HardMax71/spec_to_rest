package specrest.codegen.go

import specrest.codegen.StatePlan
import specrest.ir.Naming
import specrest.profile.ProfiledEntity
import specrest.profile.ProfiledField
import specrest.profile.ProfiledService

// Generates internal/services/state_bridge.go: hydrate a Dafny ServiceState
// from the database and persist its mutations back, both against bun.IDB so
// the kernel service methods can run them inside one RunInTx transaction.
// Dafny's Go backend doubles underscores in identifiers (Dtor_created__at)
// and returns tuple elements behind pointers; both quirks live here only.
object StateBridgeGo:

  private val ScalarGoTypes = Set("string", "int64", "bool", "time.Time")

  // Nullable fields would need Option constructors on the Dafny side; the
  // gate keeps them out until a spec actually needs them in go.
  def plan(profiled: ProfiledService): Either[String, StatePlan.Plan] =
    StatePlan.analyze(
      profiled,
      fieldSupported = f => ScalarGoTypes.contains(f.domainType) && !f.nullable,
      keySupported = k => Set("string", "int64").contains(k.domainType) && !k.nullable
    )

  private def pascal(name: String): String =
    Naming.toPascalCase(name, Naming.PascalStrategy.Go)

  private def dafnyName(specName: String): String = specName.replace("_", "__")

  // Dafny's Go backend exports a state field by capitalizing its first rune
  // and keeping the doubled underscores: base_url becomes Base__url.
  private def stateFieldName(specName: String): String =
    val d = dafnyName(specName)
    d.head.toUpper.toString + d.tail

  private def rowsVar(e: ProfiledEntity): String =
    Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "Rows"

  private def toDafnyExpr(f: ProfiledField, rowRef: String): String =
    val access = s"$rowRef.${pascal(f.fieldName)}"
    f.domainType match
      case "string"    => s"dafnykernel.StringToDafny($access)"
      case "int64"     => s"dafnykernel.IntToDafny($access)"
      case "time.Time" => s"dafnykernel.IntToDafny($access.Unix())"
      case _           => access
  private def fromDafnyExpr(f: ProfiledField, valueRef: String): String =
    val access = s"$valueRef.Dtor_${dafnyName(f.fieldName)}()"
    f.domainType match
      case "string"    => s"dafnykernel.StringFromDafny($access)"
      case "int64"     => s"dafnykernel.IntFromDafny($access)"
      case "time.Time" => s"time.Unix(dafnykernel.IntFromDafny($access), 0).UTC()"
      case _           => access

  private def keyToDafny(key: ProfiledField, rowRef: String): String =
    key.domainType match
      case "string" => s"dafnykernel.StringToDafny($rowRef.${pascal(key.fieldName)})"
      case _        => s"dafnykernel.IntToDafny($rowRef.${pascal(key.fieldName)})"

  def emit(profiled: ProfiledService, module: String): String =
    val planned = plan(profiled) match
      case Right(p) => p
      case Left(_)  => StatePlan.Plan(Nil, Nil)

    val entities   = planned.relations.map(_.entity).distinctBy(_.entityName)
    val needsTime  = entities.exists(_.fields.exists(_.domainType == "time.Time"))
    val timeImport = if needsTime then "\n\t\"time\"" else ""

    val hydrate = new StringBuilder
    for e <- entities do
      hydrate ++= s"\t${rowsVar(e)} := make([]models.${e.modelClassName}, 0)\n"
      hydrate ++= s"\tif err := db.NewSelect().Model(&${rowsVar(e)}).Scan(ctx); err != nil {\n"
      hydrate ++= "\t\treturn nil, err\n\t}\n"
    for r <- planned.relations do
      val builder = Naming.toCamelCase(r.stateField, Naming.CamelStrategy.Plain) + "Builder"
      hydrate ++= s"\t$builder := _dafny.NewMapBuilder()\n"
      hydrate ++= s"\tfor _, r := range ${rowsVar(r.entity)} {\n"
      val value = r.valueField match
        case Some(vf) => toDafnyExpr(vf, "r")
        case None =>
          val args = r.entity.fields.map(f => s"\t\t\t${toDafnyExpr(f, "r")},").mkString("\n")
          s"dafnykernel.Companion_${r.entity.entityName}_.Create_${r.entity.entityName}_(\n$args\n\t\t)"
      hydrate ++= s"\t\t$builder.Add(${keyToDafny(r.keyField, "r")}, $value)\n"
      hydrate ++= "\t}\n"
      hydrate ++= s"\tst.${stateFieldName(r.stateField)} = $builder.ToMap()\n"

    val persist = new StringBuilder
    for r <- planned.entityRowRelations do
      val e        = r.entity
      val rows     = rowsVar(e)
      val keyGo    = pascal(r.keyField.fieldName)
      val existing = Naming.toCamelCase(e.entityName, Naming.CamelStrategy.Plain) + "Existing"
      val keyType  = r.keyField.domainType
      persist ++= s"\t$rows := make([]models.${e.modelClassName}, 0)\n"
      persist ++= s"\tif err := db.NewSelect().Model(&$rows).Scan(ctx); err != nil {\n"
      persist ++= "\t\treturn err\n\t}\n"
      persist ++= s"\t$existing := make(map[$keyType]models.${e.modelClassName}, len($rows))\n"
      persist ++= s"\tfor _, r := range $rows {\n\t\t$existing[r.$keyGo] = r\n\t}\n"
      persist ++= s"\tseen := make(map[$keyType]bool)\n"
      persist ++= s"\tit := st.${stateFieldName(r.stateField)}.Items().Iterator()\n"
      persist ++= "\tfor tu, ok := it(); ok; tu, ok = it() {\n"
      persist ++= "\t\tpair := tu.(_dafny.Tuple)\n"
      val keyExpr = keyType match
        case "string" => "dafnykernel.StringFromDafny((*pair.IndexInt(0)).(_dafny.Sequence))"
        case _        => "dafnykernel.IntFromDafny((*pair.IndexInt(0)).(_dafny.Int))"
      persist ++= s"\t\tkey := $keyExpr\n"
      persist ++= s"\t\tvalue := (*pair.IndexInt(1)).(dafnykernel.${e.entityName})\n"
      persist ++= "\t\tseen[key] = true\n"
      persist ++= s"\t\trow, exists := $existing[key]\n"
      for f <- e.fields do
        persist ++= s"\t\trow.${pascal(f.fieldName)} = ${fromDafnyExpr(f, "value")}\n"
      persist ++= "\t\tif exists {\n"
      persist ++= "\t\t\tif _, err := db.NewUpdate().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t} else {\n"
      persist ++= "\t\t\tif _, err := db.NewInsert().Model(&row).Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t}\n"
      persist ++= "\t}\n"
      persist ++= s"\tfor key := range $existing {\n"
      persist ++= "\t\tif !seen[key] {\n"
      persist ++= s"\t\t\trow := $existing[key]\n"
      persist ++= "\t\t\tif _, err := db.NewDelete().Model(&row).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\t\t\treturn err\n\t\t\t}\n"
      persist ++= "\t\t}\n\t}\n"
    if planned.scalars.nonEmpty then
      persist ++= "\tscalarRow := new(models.ServiceState)\n"
      persist ++= "\terr := db.NewSelect().Model(scalarRow).Where(\"id = 1\").Limit(1).Scan(ctx)\n"
      persist ++= "\tif err != nil {\n\t\treturn err\n\t}\n"
      for sc <- planned.scalars do
        persist ++= s"\tscalarRow.${pascal(sc.columnName)} = dafnykernel.IntFromDafny(st.${stateFieldName(sc.stateField)})\n"
      persist ++= "\tif _, err := db.NewUpdate().Model(scalarRow).WherePK().Exec(ctx); err != nil {\n"
      persist ++= "\t\treturn err\n\t}\n"

    val scalarHydrate = new StringBuilder
    if planned.scalars.nonEmpty then
      scalarHydrate ++= "\tscalarRow := new(models.ServiceState)\n"
      scalarHydrate ++= "\tif err := db.NewSelect().Model(scalarRow).Where(\"id = 1\").Limit(1).Scan(ctx); err == nil {\n"
      for sc <- planned.scalars do
        scalarHydrate ++= s"\t\tst.${stateFieldName(sc.stateField)} = dafnykernel.IntToDafny(scalarRow.${pascal(sc.columnName)})\n"
      scalarHydrate ++= "\t}\n"

    s"""package services
       |
       |import (
       |\t"context"$timeImport
       |
       |\t"github.com/uptrace/bun"
       |
       |\tdafnykernel "$module/internal/dafnykernel"
       |\t_dafny "$module/internal/dafnykernel/dafny"
       |\t"$module/internal/models"
       |)
       |
       |func hydrateState(ctx context.Context, db bun.IDB) (*dafnykernel.ServiceState, error) {
       |\tst := dafnykernel.MakeState()
       |${hydrate.toString}${scalarHydrate.toString}\treturn st, nil
       |}
       |
       |func persistState(ctx context.Context, db bun.IDB, st *dafnykernel.ServiceState) error {
       |${persist.toString}\treturn nil
       |}
       |""".stripMargin
