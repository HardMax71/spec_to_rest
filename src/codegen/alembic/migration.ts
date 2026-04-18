import type {
  DatabaseSchema,
  TableSpec,
  ColumnSpec,
} from "#convention/types.js";

export interface AlembicColumn {
  readonly name: string;
  readonly saType: string;
  readonly nullable: boolean;
  readonly primaryKey: boolean;
  readonly autoincrement: boolean;
  readonly serverDefault: string | null;
}

export interface AlembicForeignKey {
  readonly name: string;
  readonly column: string;
  readonly refTable: string;
  readonly refColumn: string;
  readonly onDelete: string;
}

export interface AlembicCheck {
  readonly name: string;
  readonly sql: string;
}

export interface AlembicIndex {
  readonly name: string;
  readonly table: string;
  readonly columns: readonly string[];
  readonly unique: boolean;
}

export interface AlembicTable {
  readonly name: string;
  readonly entityName: string;
  readonly columns: readonly AlembicColumn[];
  readonly foreignKeys: readonly AlembicForeignKey[];
  readonly checks: readonly AlembicCheck[];
  readonly indexes: readonly AlembicIndex[];
  readonly tableArgs: readonly string[];
}

export interface AlembicMigration {
  readonly revision: string;
  readonly createdDate: string;
  readonly tables: readonly AlembicTable[];
  readonly tablesReversed: readonly AlembicTable[];
  readonly needsPostgresDialect: boolean;
}

export interface BuildMigrationOptions {
  readonly revision?: string;
  readonly createdDate?: string;
}

export function buildAlembicMigration(
  schema: DatabaseSchema,
  opts: BuildMigrationOptions = {},
): AlembicMigration {
  const sorted = topoSortTables(schema.tables);
  const tables = sorted.map((t) => buildAlembicTable(t));
  const needsPostgresDialect = tables.some((t) =>
    t.columns.some((c) => c.saType.startsWith("postgresql.")),
  );
  return {
    revision: opts.revision ?? "001",
    createdDate: opts.createdDate ?? new Date().toISOString().slice(0, 10),
    tables,
    tablesReversed: [...tables].reverse(),
    needsPostgresDialect,
  };
}

function topoSortTables(tables: readonly TableSpec[]): TableSpec[] {
  const byName = new Map(tables.map((t) => [t.name, t]));
  const visited = new Set<string>();
  const result: TableSpec[] = [];

  function visit(t: TableSpec): void {
    if (visited.has(t.name)) return;
    visited.add(t.name);
    for (const fk of t.foreignKeys) {
      const target = byName.get(fk.refTable);
      if (target !== undefined && target !== t) visit(target);
    }
    result.push(t);
  }
  for (const t of tables) visit(t);
  return result;
}

function buildAlembicTable(t: TableSpec): AlembicTable {
  const columns = t.columns.map((c) => buildColumn(c, t));
  const foreignKeys = t.foreignKeys.map((fk) => ({
    name: `fk_${t.name}_${fk.column}`,
    column: fk.column,
    refTable: fk.refTable,
    refColumn: fk.refColumn,
    onDelete: fk.onDelete,
  }));
  const checks = t.checks.map((sql, i) => ({
    name: `ck_${t.name}_${i}`,
    sql,
  }));
  const indexes = t.indexes.map((ix) => ({
    name: ix.name,
    table: t.name,
    columns: ix.columns,
    unique: ix.unique,
  }));
  const tableArgs = [
    ...columns.map(renderColumnCall),
    ...foreignKeys.map(renderForeignKeyCall),
    ...checks.map(renderCheckCall),
  ];
  return {
    name: t.name,
    entityName: t.entityName,
    columns,
    foreignKeys,
    checks,
    indexes,
    tableArgs,
  };
}

function buildColumn(c: ColumnSpec, t: TableSpec): AlembicColumn {
  const isPk = c.name === t.primaryKey;
  const isSerial = c.sqlType === "BIGSERIAL" || c.sqlType === "SERIAL";
  return {
    name: c.name,
    saType: mapSqlTypeToSa(c.sqlType),
    nullable: c.nullable,
    primaryKey: isPk,
    autoincrement: isSerial,
    serverDefault: mapServerDefault(c.defaultValue),
  };
}

function mapSqlTypeToSa(sqlType: string): string {
  const direct = DIRECT_SA_TYPES.get(sqlType);
  if (direct !== undefined) return direct;
  const numericMatch = sqlType.match(/^NUMERIC\((\d+)\s*,\s*(\d+)\)$/);
  if (numericMatch) return `sa.Numeric(${numericMatch[1]}, ${numericMatch[2]})`;
  const varcharMatch = sqlType.match(/^VARCHAR\((\d+)\)$/);
  if (varcharMatch) return `sa.String(length=${varcharMatch[1]})`;
  return "sa.Text()";
}

const DIRECT_SA_TYPES: ReadonlyMap<string, string> = new Map([
  ["TEXT", "sa.Text()"],
  ["BIGSERIAL", "sa.BigInteger()"],
  ["BIGINT", "sa.BigInteger()"],
  ["INTEGER", "sa.Integer()"],
  ["SERIAL", "sa.Integer()"],
  ["BOOLEAN", "sa.Boolean()"],
  ["DOUBLE PRECISION", "sa.Float()"],
  ["DATE", "sa.Date()"],
  ["TIMESTAMPTZ", "sa.DateTime(timezone=True)"],
  ["UUID", "sa.Uuid()"],
  ["BYTEA", "sa.LargeBinary()"],
  ["JSONB", "postgresql.JSONB()"],
]);

function mapServerDefault(value: string | null): string | null {
  if (value === null) return null;
  if (value === "NOW()") return "sa.func.now()";
  return `sa.text(${pythonStringLiteral(value)})`;
}

function pythonStringLiteral(s: string): string {
  if (!s.includes("'")) return `'${s}'`;
  if (!s.includes('"')) return `"${s}"`;
  return `"${s.replace(/"/g, '\\"')}"`;
}

function renderColumnCall(c: AlembicColumn): string {
  const parts: string[] = [`"${c.name}"`, c.saType];
  if (c.primaryKey) parts.push("primary_key=True");
  if (c.autoincrement) parts.push("autoincrement=True");
  if (c.serverDefault !== null) parts.push(`server_default=${c.serverDefault}`);
  parts.push(`nullable=${c.nullable ? "True" : "False"}`);
  return `sa.Column(${parts.join(", ")})`;
}

function renderForeignKeyCall(fk: AlembicForeignKey): string {
  return `sa.ForeignKeyConstraint(["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"], ondelete="${fk.onDelete}", name="${fk.name}")`;
}

function renderCheckCall(c: AlembicCheck): string {
  return `sa.CheckConstraint(${pythonStringLiteral(c.sql)}, name="${c.name}")`;
}
