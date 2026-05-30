package specrest.testgen

import specrest.convention.Naming
import specrest.ir.generated.SpecRestGenerated.entFields
import specrest.ir.generated.SpecRestGenerated.entName
import specrest.ir.generated.SpecRestGenerated.entity_decl_full
import specrest.ir.generated.SpecRestGenerated.fldName
import specrest.ir.generated.SpecRestGenerated.fldType
import specrest.ir.generated.SpecRestGenerated.irStateFields
import specrest.ir.generated.SpecRestGenerated.isDateTimeType
import specrest.ir.generated.SpecRestGenerated.stfName
import specrest.ir.generated.SpecRestGenerated.svcEntities
import specrest.ir.generated.SpecRestGenerated.svcTransitions
import specrest.ir.generated.SpecRestGenerated.svcTypeAliases
import specrest.ir.generated.SpecRestGenerated.trnEntity
import specrest.profile.ProfiledService

object AdminRouterTs:

  final private case class Col(tsField: String, columnName: String, isDate: Boolean)

  def emit(profiled: ProfiledService): String =
    val ir       = profiled.ir
    val entities = svcEntities(ir)

    def colsOf(e: entity_decl_full): List[Col] =
      entFields(e).map: f =>
        Col(
          tsField = Naming.toCamelCase(fldName(f)),
          columnName = Naming.toColumnName(fldName(f)),
          isDate = isDateTimeType(svcTypeAliases(ir), fldType(f))
        )

    def accessor(e: entity_decl_full): String = Naming.toCamelCase(entName(e))

    val rowToDictFns = entities
      .map: e =>
        val cols = colsOf(e)
        val pairs = cols
          .map: c =>
            val v =
              if c.isDate then
                s"r.${c.tsField} == null ? null : new Date(r.${c.tsField} as string | Date).toISOString()"
              else s"r.${c.tsField}"
            s"""    ${tsKey(c.columnName)}: $v,"""
          .mkString("\n")
        s"""function rowToDict_${entName(e)}(r: Record<string, unknown>): Record<string, unknown> {
           |  return {
           |$pairs
           |  };
           |}""".stripMargin
      .mkString("\n\n")

    val resetStmts =
      if entities.isEmpty then "      // no entities"
      else
        entities.reverse
          .map(e => s"      await (prisma as unknown as AnyPrisma).${accessor(e)}.deleteMany();")
          .mkString("\n")

    val stateFields = irStateFields(ir)
    val neededEntities = stateFields
      .flatMap(f => AdminModel.projectionFor(f, ir).map(_.entityName))
      .distinct
    val rowsDecls = neededEntities
      .flatMap(en => entities.find(e => entName(e) == en))
      .map: e =>
        s"      const rows_${entName(e)} = await (prisma as unknown as AnyPrisma).${accessor(e)}.findMany();"
      .mkString("\n")
    val stateProps = stateFields
      .map: f =>
        AdminModel.projectionFor(f, ir) match
          case Some(p) =>
            val keyTs = Naming.toCamelCase(p.keyFieldName)
            val value = p.valueShape match
              case AdminModel.ProjectionValue.EntityRow =>
                s"rowToDict_${p.entityName}(r)"
              case AdminModel.ProjectionValue.PrimitiveField(name) =>
                s"r.${Naming.toCamelCase(name)}"
            s"""        ${tsKey(stfName(f))}: Object.fromEntries(
               |          rows_${p.entityName}.map((r: Record<string, unknown>) => [
               |            String(r.$keyTs), $value,
               |          ]),
               |        ),""".stripMargin
          case None =>
            s"        ${tsKey(stfName(f))}: null,"
      .mkString("\n")

    val seedEntities = svcTransitions(ir).map(trnEntity).toSet
    val seedTargets  = entities.filter(e => seedEntities.contains(entName(e)))
    val seedHandlers = seedTargets
      .map: e =>
        val snake = Naming.toSnakeCase(entName(e))
        val pk    = AdminModel.primaryKeyField(e).getOrElse("id")
        val pkTs  = Naming.toCamelCase(pk)
        val cols  = colsOf(e)
        val dataPairs = cols
          .map: c =>
            val src =
              if c.isDate then
                s"""body[${jsStr(c.columnName)}] == null ? undefined : new Date(body[${jsStr(
                    c.columnName
                  )}] as string)"""
              else s"""body[${jsStr(c.columnName)}]"""
            s"""          ${c.tsField}: $src,"""
          .mkString("\n")
        s"""  app.post('/__test_admin__/seed/$snake', (req: Request, res: Response): void => {
           |    if (!enabled()) {
           |      res.status(403).json({ detail: 'test admin disabled' });
           |      return;
           |    }
           |    void (async () => {
           |      const body = req.body as Record<string, unknown>;
           |      const created = await (prisma as unknown as AnyPrisma).${accessor(e)}.create({
           |        data: {
           |$dataPairs
           |        },
           |      });
           |      res.status(201).json({ ${tsKey(pk)}: created.$pkTs });
           |    })().catch((e: unknown) => {
           |      res.status(500).json({ detail: String(e) });
           |    });
           |  });""".stripMargin
      .mkString("\n\n")
    val seedSection =
      if seedTargets.isEmpty then "" else "\n" + seedHandlers + "\n"

    s"""import type { Express, Request, Response } from 'express';

import { prisma } from '../prisma.js';

// The conformance suite (tests/) is the spec-derived, language-agnostic HTTP black-box
// driver shared with the fastapi target; this router exposes the identical test-admin
// contract (reset / state / seed) so the same suite can run against ts-express.

type AnyPrisma = Record<string, {
  deleteMany: () => Promise<unknown>;
  findMany: () => Promise<Array<Record<string, unknown>>>;
  create: (args: { data: Record<string, unknown> }) => Promise<Record<string, unknown>>;
}>;

const enabled = (): boolean => process.env.ENABLE_TEST_ADMIN === '1';

$rowToDictFns

export const registerTestAdminRoutes = (app: Express): void => {
  app.post('/__test_admin__/reset', (_req: Request, res: Response): void => {
    if (!enabled()) {
      res.status(403).json({ detail: 'test admin disabled' });
      return;
    }
    void (async () => {
$resetStmts
      res.status(204).end();
    })().catch((e: unknown) => {
      res.status(500).json({ detail: String(e) });
    });
  });

  app.get('/__test_admin__/state', (_req: Request, res: Response): void => {
    if (!enabled()) {
      res.status(403).json({ detail: 'test admin disabled' });
      return;
    }
    void (async () => {
$rowsDecls
      res.status(200).json({
$stateProps
      });
    })().catch((e: unknown) => {
      res.status(500).json({ detail: String(e) });
    });
  });
$seedSection};
""".stripMargin

  private def tsKey(s: String): String =
    if s.matches("[A-Za-z_][A-Za-z0-9_]*") then s else jsStr(s)

  private def jsStr(s: String): String =
    "'" + s.replace("\\", "\\\\").replace("'", "\\'") + "'"
