import type {
  ServiceIR,
  EntityDecl,
  FieldDecl,
  TypeExpr,
  Expr,
  TypeAliasDecl,
  EnumDecl,
  StateFieldDecl,
} from "#ir/types.js";
import type {
  DatabaseSchema,
  TableSpec,
  ColumnSpec,
  ForeignKeySpec,
  IndexSpec,
} from "#convention/types.js";
import { toTableName, toColumnName, toSnakeCase } from "#convention/naming.js";
import { getConvention } from "#convention/path.js";

export function deriveSchema(ir: ServiceIR): DatabaseSchema {
  const entityNames = new Set(ir.entities.map((e) => e.name));
  const enumMap = new Map(ir.enums.map((e) => [e.name, e]));
  const aliasMap = new Map(ir.typeAliases.map((a) => [a.name, a]));

  const tables: TableSpec[] = [];

  for (const entity of ir.entities) {
    tables.push(deriveTable(entity, ir, entityNames, enumMap, aliasMap));
  }

  if (ir.state) {
    for (const field of ir.state.fields) {
      if (field.typeExpr.kind !== "RelationType") continue;
      if (field.typeExpr.multiplicity === "some" || field.typeExpr.multiplicity === "set") {
        const junction = deriveJunctionTable(field, entityNames, enumMap, aliasMap);
        if (junction) tables.push(junction);
      }
    }
  }

  return { tables };
}

function deriveTable(
  entity: EntityDecl,
  ir: ServiceIR,
  entityNames: Set<string>,
  enumMap: Map<string, EnumDecl>,
  aliasMap: Map<string, TypeAliasDecl>,
): TableSpec {
  const tableNameOverride = getConvention(ir.conventions, entity.name, "db_table");
  const tableName = tableNameOverride || toTableName(entity.name);

  const entityFieldNames = new Set(entity.fields.map((f) => f.name));

  const columns: ColumnSpec[] = [];
  if (!entityFieldNames.has("id")) {
    columns.push({ name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null });
  }

  const foreignKeys: ForeignKeySpec[] = [];
  const checks: string[] = [];
  const indexes: IndexSpec[] = [];

  for (const field of entity.fields) {
    const colName = toColumnName(field.name);
    const mapped = mapFieldToColumn(field, entityNames, enumMap, aliasMap);

    columns.push(mapped.column);

    if (mapped.foreignKey) {
      foreignKeys.push(mapped.foreignKey);
      indexes.push({
        name: `idx_${tableName}_${colName}`,
        columns: [colName],
        unique: false,
      });
    }

    if (mapped.check) checks.push(mapped.check);

    if (field.constraint) {
      const constraintChecks = extractChecks(colName, field.constraint);
      checks.push(...constraintChecks);
    }
  }

  for (const inv of entity.invariants) {
    const invChecks = extractInvariantChecks(inv, entity.fields);
    checks.push(...invChecks);
  }

  if (ir.state) {
    for (const sf of ir.state.fields) {
      if (sf.typeExpr.kind !== "RelationType") continue;
      const toName = resolveTypeName(sf.typeExpr.toType);
      if (toName !== entity.name) continue;
      if (sf.typeExpr.multiplicity === "some" || sf.typeExpr.multiplicity === "set") continue;

      const fromName = resolveTypeName(sf.typeExpr.fromType);
      if (fromName && entityNames.has(fromName)) {
        const fkCol = toColumnName(fromName.replace(/([A-Z])/g, "_$1").toLowerCase().replace(/^_/, "")) + "_id";
        const fkRefTable = toTableName(fromName);
        const nullable = sf.typeExpr.multiplicity === "lone";

        if (!columns.some((c) => c.name === fkCol)) {
          columns.push({
            name: fkCol,
            sqlType: "BIGINT",
            nullable,
            defaultValue: null,
          });
          foreignKeys.push({
            column: fkCol,
            refTable: fkRefTable,
            refColumn: "id",
            onDelete: "CASCADE",
          });
          indexes.push({
            name: `idx_${tableName}_${fkCol}`,
            columns: [fkCol],
            unique: false,
          });
        }
      }
    }
  }

  const tsOverride = getConvention(ir.conventions, entity.name, "db_timestamps");
  const addTimestamps = tsOverride !== "false";

  if (addTimestamps && !columns.some((c) => c.name === "created_at")) {
    columns.push({ name: "created_at", sqlType: "TIMESTAMPTZ", nullable: false, defaultValue: "NOW()" });
  }
  if (addTimestamps && !columns.some((c) => c.name === "updated_at")) {
    columns.push({ name: "updated_at", sqlType: "TIMESTAMPTZ", nullable: false, defaultValue: "NOW()" });
  }

  return {
    name: tableName,
    entityName: entity.name,
    columns,
    primaryKey: "id",
    foreignKeys,
    checks,
    indexes,
  };
}

function deriveJunctionTable(
  stateField: StateFieldDecl,
  entityNames: Set<string>,
  enumMap: Map<string, EnumDecl>,
  aliasMap: Map<string, TypeAliasDecl>,
): TableSpec | null {
  if (stateField.typeExpr.kind !== "RelationType") return null;

  const fromName = resolveTypeName(stateField.typeExpr.fromType);
  const toName = resolveTypeName(stateField.typeExpr.toType);
  if (!fromName || !toName) return null;
  if (!entityNames.has(fromName) || !entityNames.has(toName)) return null;

  const fromTable = toTableName(fromName);
  const toTable = toTableName(toName);
  const tableName = `${fromTable}_${toTable}`;

  const fromCol = toColumnName(fromName.replace(/([A-Z])/g, "_$1").toLowerCase().replace(/^_/, "")) + "_id";
  const toCol = toColumnName(toName.replace(/([A-Z])/g, "_$1").toLowerCase().replace(/^_/, "")) + "_id";

  return {
    name: tableName,
    entityName: `${fromName}_${toName}`,
    columns: [
      { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
      { name: fromCol, sqlType: "BIGINT", nullable: false, defaultValue: null },
      { name: toCol, sqlType: "BIGINT", nullable: false, defaultValue: null },
      { name: "created_at", sqlType: "TIMESTAMPTZ", nullable: false, defaultValue: "NOW()" },
    ],
    primaryKey: "id",
    foreignKeys: [
      { column: fromCol, refTable: fromTable, refColumn: "id", onDelete: "CASCADE" },
      { column: toCol, refTable: toTable, refColumn: "id", onDelete: "CASCADE" },
    ],
    checks: [],
    indexes: [
      {
        name: `idx_${tableName}_${fromCol}_${toCol}`,
        columns: [fromCol, toCol],
        unique: true,
      },
      { name: `idx_${tableName}_${fromCol}`, columns: [fromCol], unique: false },
      { name: `idx_${tableName}_${toCol}`, columns: [toCol], unique: false },
    ],
  };
}

interface MappedField {
  column: ColumnSpec;
  foreignKey: ForeignKeySpec | null;
  check: string | null;
}

function mapFieldToColumn(
  field: FieldDecl,
  entityNames: Set<string>,
  enumMap: Map<string, EnumDecl>,
  aliasMap: Map<string, TypeAliasDecl>,
): MappedField {
  const colName = toColumnName(field.name);
  const mapped = mapTypeToColumn(colName, field.typeExpr, entityNames, enumMap, aliasMap);

  if (mapped.foreignKey === null && colName.endsWith("_id")) {
    const prefix = colName.slice(0, -"_id".length);
    const targetEntity = [...entityNames].find((n) => toSnakeCase(n) === prefix);
    if (targetEntity !== undefined) {
      return {
        column: { ...mapped.column, sqlType: "BIGINT" },
        foreignKey: { column: colName, refTable: toTableName(targetEntity), refColumn: "id", onDelete: "CASCADE" },
        check: mapped.check,
      };
    }
  }

  return mapped;
}

function mapTypeToColumn(
  colName: string,
  typeExpr: TypeExpr,
  entityNames: Set<string>,
  enumMap: Map<string, EnumDecl>,
  aliasMap: Map<string, TypeAliasDecl>,
): MappedField {
  switch (typeExpr.kind) {
    case "NamedType": {
      const name = typeExpr.name;

      const sqlType = PRIMITIVE_TYPE_MAP.get(name);
      if (sqlType) {
        return { column: { name: colName, sqlType, nullable: false, defaultValue: null }, foreignKey: null, check: null };
      }

      const enumDecl = enumMap.get(name);
      if (enumDecl) {
        const values = enumDecl.values.map((v) => `'${escapeSqlString(v)}'`).join(", ");
        return {
          column: { name: colName, sqlType: "TEXT", nullable: false, defaultValue: null },
          foreignKey: null,
          check: `${colName} IN (${values})`,
        };
      }

      if (entityNames.has(name)) {
        const refTable = toTableName(name);
        return {
          column: { name: colName + "_id", sqlType: "BIGINT", nullable: false, defaultValue: null },
          foreignKey: { column: colName + "_id", refTable, refColumn: "id", onDelete: "CASCADE" },
          check: null,
        };
      }

      const alias = aliasMap.get(name);
      if (alias) {
        return mapTypeToColumn(colName, alias.typeExpr, entityNames, enumMap, aliasMap);
      }

      return { column: { name: colName, sqlType: "TEXT", nullable: false, defaultValue: null }, foreignKey: null, check: null };
    }

    case "OptionType": {
      const inner = mapTypeToColumn(colName, typeExpr.innerType, entityNames, enumMap, aliasMap);
      return {
        column: { ...inner.column, nullable: true },
        foreignKey: inner.foreignKey ? { ...inner.foreignKey } : null,
        check: inner.check,
      };
    }

    case "SetType":
      return { column: { name: colName, sqlType: "JSONB", nullable: false, defaultValue: "'[]'::jsonb" }, foreignKey: null, check: null };

    case "SeqType":
      return { column: { name: colName, sqlType: "JSONB", nullable: false, defaultValue: "'[]'::jsonb" }, foreignKey: null, check: null };

    case "MapType":
      return { column: { name: colName, sqlType: "JSONB", nullable: false, defaultValue: "'{}'::jsonb" }, foreignKey: null, check: null };

    case "RelationType":
      return { column: { name: colName, sqlType: "BIGINT", nullable: false, defaultValue: null }, foreignKey: null, check: null };
  }
}

const PRIMITIVE_TYPE_MAP: ReadonlyMap<string, string> = new Map([
  ["String", "TEXT"],
  ["Int", "INTEGER"],
  ["Float", "DOUBLE PRECISION"],
  ["Bool", "BOOLEAN"],
  ["Boolean", "BOOLEAN"],
  ["DateTime", "TIMESTAMPTZ"],
  ["Date", "DATE"],
  ["UUID", "UUID"],
  ["Decimal", "NUMERIC(19,4)"],
  ["Bytes", "BYTEA"],
  ["Money", "INTEGER"],
]);

function escapeSqlString(value: string): string {
  return value.replace(/'/g, "''");
}

function resolveTypeName(typeExpr: TypeExpr): string | null {
  if (typeExpr.kind === "NamedType") return typeExpr.name;
  return null;
}

function extractChecks(colName: string, constraint: Expr): string[] {
  const checks: string[] = [];
  visitConstraint(constraint, colName, checks);
  return checks;
}

function visitConstraint(expr: Expr, colName: string, checks: string[]): void {
  if (expr.kind === "BinaryOp" && expr.op === "and") {
    visitConstraint(expr.left, colName, checks);
    visitConstraint(expr.right, colName, checks);
    return;
  }

  if (expr.kind === "BinaryOp") {
    const check = tryMapComparison(expr, colName);
    if (check) checks.push(check);
    return;
  }

  if (expr.kind === "Matches" && expr.expr.kind === "Identifier") {
    checks.push(`${colName} ~ '${escapeSqlString(expr.pattern)}'`);
  }
}

function tryMapComparison(expr: Expr & { kind: "BinaryOp" }, colName: string): string | null {
  const sqlOps: Record<string, string> = {
    ">": ">",
    "<": "<",
    ">=": ">=",
    "<=": "<=",
    "=": "=",
    "!=": "!=",
  };

  const sqlOp = sqlOps[expr.op];
  if (!sqlOp) return null;

  if (isLenCall(expr.left) && isLiteral(expr.right)) {
    return `length(${colName}) ${sqlOp} ${literalValue(expr.right)}`;
  }

  if (isValueRef(expr.left) && isLiteral(expr.right)) {
    return `${colName} ${sqlOp} ${literalValue(expr.right)}`;
  }

  return null;
}

function isLenCall(expr: Expr): boolean {
  return (
    expr.kind === "Call" &&
    expr.callee.kind === "Identifier" &&
    expr.callee.name === "len"
  );
}

function isValueRef(expr: Expr): boolean {
  return expr.kind === "Identifier" && expr.name === "value";
}

function isLiteral(expr: Expr): boolean {
  return expr.kind === "IntLit" || expr.kind === "FloatLit" || expr.kind === "StringLit";
}

function literalValue(expr: Expr): string {
  switch (expr.kind) {
    case "IntLit":
      return String(expr.value);
    case "FloatLit":
      return String(expr.value);
    case "StringLit":
      return `'${escapeSqlString(expr.value)}'`;
    default:
      return "NULL";
  }
}

function extractInvariantChecks(inv: Expr, fields: readonly FieldDecl[]): string[] {
  const checks: string[] = [];

  if (inv.kind === "BinaryOp" && inv.op === "and") {
    checks.push(...extractInvariantChecks(inv.left, fields));
    checks.push(...extractInvariantChecks(inv.right, fields));
    return checks;
  }

  if (inv.kind === "BinaryOp") {
    const check = tryMapInvariantComparison(inv, fields);
    if (check) checks.push(check);
  }

  if (inv.kind === "Matches") {
    const fieldName = extractFieldName(inv.expr);
    if (fieldName) {
      checks.push(`${toColumnName(fieldName)} ~ '${escapeSqlString(inv.pattern)}'`);
    }
  }

  if (inv.kind === "BinaryOp" && inv.op === "in" && inv.right.kind === "SetLiteral") {
    const fieldName = extractFieldName(inv.left);
    if (fieldName) {
      const values = inv.right.elements
        .map((e) => {
          if (e.kind === "StringLit") return `'${escapeSqlString(e.value)}'`;
          if (e.kind === "Identifier") return `'${escapeSqlString(e.name)}'`;
          if (e.kind === "EnumAccess") return `'${escapeSqlString(e.member)}'`;
          return null;
        })
        .filter(Boolean);
      if (values.length > 0) {
        checks.push(`${toColumnName(fieldName)} IN (${values.join(", ")})`);
      }
    }
  }

  return checks;
}

function tryMapInvariantComparison(
  expr: Expr & { kind: "BinaryOp" },
  fields: readonly FieldDecl[],
): string | null {
  const sqlOps: Record<string, string> = {
    ">": ">",
    "<": "<",
    ">=": ">=",
    "<=": "<=",
    "=": "=",
    "!=": "!=",
  };

  const sqlOp = sqlOps[expr.op];
  if (!sqlOp) return null;

  if (isLenCall(expr.left) && isLiteral(expr.right)) {
    const arg = (expr.left as { kind: "Call"; args: readonly Expr[] }).args[0];
    const fieldName = extractFieldName(arg);
    if (fieldName && fields.some((f) => f.name === fieldName)) {
      return `length(${toColumnName(fieldName)}) ${sqlOp} ${literalValue(expr.right)}`;
    }
  }

  const leftField = extractFieldName(expr.left);
  if (leftField && isLiteral(expr.right) && fields.some((f) => f.name === leftField)) {
    return `${toColumnName(leftField)} ${sqlOp} ${literalValue(expr.right)}`;
  }

  return null;
}

function extractFieldName(expr: Expr): string | null {
  if (expr.kind === "Identifier") return expr.name;
  if (expr.kind === "FieldAccess" && expr.base.kind === "Identifier") return expr.field;
  return null;
}
