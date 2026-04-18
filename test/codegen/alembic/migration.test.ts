import { spawnSync } from "node:child_process";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { buildAlembicMigration } from "#codegen/alembic/migration.js";
import { emitProject } from "#codegen/emit.js";
import { buildIR } from "#ir/index.js";
import { parseSpec } from "#parser/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import type { ProfiledService } from "#profile/types.js";

const fixtureDir = join(import.meta.dirname, "../../parser/fixtures");

function profiledFrom(name: string): ProfiledService {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  return buildProfiledService(buildIR(tree), "python-fastapi-postgres");
}

function migrationContent(fixture: string): string {
  const files = emitProject(profiledFrom(fixture));
  const file = files.find((f) =>
    f.path === "alembic/versions/001_initial_schema.py",
  );
  expect(file).toBeDefined();
  return file!.content;
}

function python3Available(): boolean {
  const probe = spawnSync("python3", ["--version"], { encoding: "utf-8" });
  return probe.status === 0;
}

describe("buildAlembicMigration — render context shape", () => {
  it("stamps revision 001 and a YYYY-MM-DD created date by default", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const migration = buildAlembicMigration(profiled.schema);
    expect(migration.revision).toBe("001");
    expect(migration.createdDate).toMatch(/^\d{4}-\d{2}-\d{2}$/);
  });

  it("exposes options for deterministic output in tests", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const migration = buildAlembicMigration(profiled.schema, {
      revision: "007",
      createdDate: "2026-04-18",
    });
    expect(migration.revision).toBe("007");
    expect(migration.createdDate).toBe("2026-04-18");
  });

  it("topo-sorts tables so FK targets come before referrers", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const migration = buildAlembicMigration(profiled.schema);
    const position = new Map(
      migration.tables.map((t, i) => [t.name, i] as const),
    );
    for (const table of migration.tables) {
      for (const fk of table.foreignKeys) {
        const targetPos = position.get(fk.refTable);
        expect(
          targetPos,
          `FK target ${fk.refTable} missing from migration.tables`,
        ).toBeDefined();
        expect(targetPos!).toBeLessThan(position.get(table.name)!);
      }
    }
  });

  it("throws on foreign-key cycles instead of silently producing an invalid order", () => {
    const cyclicSchema = {
      tables: [
        {
          name: "a",
          entityName: "A",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "b_id", sqlType: "BIGINT", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [
            { column: "b_id", refTable: "b", refColumn: "id", onDelete: "CASCADE" },
          ],
          checks: [],
          indexes: [],
        },
        {
          name: "b",
          entityName: "B",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "a_id", sqlType: "BIGINT", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [
            { column: "a_id", refTable: "a", refColumn: "id", onDelete: "CASCADE" },
          ],
          checks: [],
          indexes: [],
        },
      ],
    };
    expect(() => buildAlembicMigration(cyclicSchema)).toThrow(
      /Foreign-key cycle detected/,
    );
  });

  it("accepts self-referential FKs without mistaking them for a cycle", () => {
    const selfRefSchema = {
      tables: [
        {
          name: "nodes",
          entityName: "Node",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "parent_id", sqlType: "BIGINT", nullable: true, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [
            { column: "parent_id", refTable: "nodes", refColumn: "id", onDelete: "CASCADE" },
          ],
          checks: [],
          indexes: [],
        },
      ],
    };
    expect(() => buildAlembicMigration(selfRefSchema)).not.toThrow();
  });

  it("tablesReversed reverses tables (for downgrade drop order)", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const migration = buildAlembicMigration(profiled.schema);
    expect(migration.tablesReversed.map((t) => t.name)).toEqual(
      [...migration.tables].reverse().map((t) => t.name),
    );
  });

  it("needsPostgresDialect is true when any column is JSONB", () => {
    const migration = buildAlembicMigration({
      tables: [
        {
          name: "t",
          entityName: "T",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "payload", sqlType: "JSONB", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [],
          checks: [],
          indexes: [],
        },
      ],
    });
    expect(migration.needsPostgresDialect).toBe(true);
  });

  it("needsPostgresDialect is false when no column is JSONB", () => {
    const migration = buildAlembicMigration({
      tables: [
        {
          name: "t",
          entityName: "T",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "name", sqlType: "TEXT", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [],
          checks: [],
          indexes: [],
        },
      ],
    });
    expect(migration.needsPostgresDialect).toBe(false);
  });
});

describe("buildAlembicMigration — SQL-type → SQLAlchemy mapping", () => {
  it("maps convention PG types to SQLAlchemy type expressions", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const migration = buildAlembicMigration(profiled.schema);
    const urlMappings = migration.tables.find((t) => t.name === "url_mappings");
    expect(urlMappings).toBeDefined();
    const columns = new Map(urlMappings!.columns.map((c) => [c.name, c]));

    expect(columns.get("id")?.saType).toBe("sa.BigInteger()");
    expect(columns.get("id")?.primaryKey).toBe(true);
    expect(columns.get("id")?.autoincrement).toBe(true);
    expect(columns.get("code")?.saType).toBe("sa.Text()");
    expect(columns.get("click_count")?.saType).toBe("sa.Integer()");
    expect(columns.get("created_at")?.saType).toBe("sa.DateTime(timezone=True)");
  });

  it("handles NUMERIC(p) without a scale argument", () => {
    const migration = buildAlembicMigration({
      tables: [
        {
          name: "t",
          entityName: "T",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "amount_p", sqlType: "NUMERIC(10)", nullable: false, defaultValue: null },
            { name: "amount_ps", sqlType: "NUMERIC(19,4)", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [],
          checks: [],
          indexes: [],
        },
      ],
    });
    const cols = new Map(migration.tables[0].columns.map((c) => [c.name, c]));
    expect(cols.get("amount_p")?.saType).toBe("sa.Numeric(10)");
    expect(cols.get("amount_ps")?.saType).toBe("sa.Numeric(19, 4)");
  });

  it("throws on a truly unsupported SQL type instead of silently degrading to sa.Text()", () => {
    expect(() =>
      buildAlembicMigration({
        tables: [
          {
            name: "t",
            entityName: "T",
            columns: [
              { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
              { name: "odd", sqlType: "MONEY_EXT", nullable: false, defaultValue: null },
            ],
            primaryKey: "id",
            foreignKeys: [],
            checks: [],
            indexes: [],
          },
        ],
      }),
    ).toThrow(/Unsupported SQL type in Alembic migration: MONEY_EXT/);
  });

  it("escapes backslashes and control chars so Python literals stay valid", () => {
    const migration = buildAlembicMigration({
      tables: [
        {
          name: "t",
          entityName: "T",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
          ],
          primaryKey: "id",
          foreignKeys: [],
          checks: [
            // regex with backslash and a newline — both must be escaped
            "code ~ '^\\d+$'",
            "note != 'line-1\nline-2'",
          ],
          indexes: [],
        },
      ],
    });
    const args = migration.tables[0].tableArgs.filter((a) =>
      a.includes("CheckConstraint"),
    );
    expect(args[0]).toContain("^\\\\d+$");
    expect(args[1]).toContain("line-1\\nline-2");
  });

  it("translates convention defaults to SQLAlchemy expressions", () => {
    const migration = buildAlembicMigration({
      tables: [
        {
          name: "t",
          entityName: "T",
          columns: [
            { name: "id", sqlType: "BIGSERIAL", nullable: false, defaultValue: null },
            { name: "created_at", sqlType: "TIMESTAMPTZ", nullable: false, defaultValue: "NOW()" },
            { name: "payload", sqlType: "JSONB", nullable: false, defaultValue: "'[]'::jsonb" },
          ],
          primaryKey: "id",
          foreignKeys: [],
          checks: [],
          indexes: [],
        },
      ],
    });
    const cols = new Map(migration.tables[0].columns.map((c) => [c.name, c]));
    expect(cols.get("id")?.serverDefault).toBeNull();
    expect(cols.get("created_at")?.serverDefault).toBe("sa.func.now()");
    expect(cols.get("payload")?.serverDefault).toBe("sa.text(\"'[]'::jsonb\")");
  });
});

describe("buildAlembicMigration — per-fixture structural coverage", () => {
  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
    "edge_cases.spec",
  ])("includes every derived table as op.create_table in %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const content = migrationContent(fixture);
    for (const table of profiled.schema.tables) {
      expect(content).toContain(`op.create_table(\n        "${table.name}",`);
    }
  });

  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
  ])("every derived CHECK constraint surfaces as a named CheckConstraint in %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const content = migrationContent(fixture);
    let checkCount = 0;
    for (const table of profiled.schema.tables) {
      for (let i = 0; i < table.checks.length; i += 1) {
        const name = `ck_${table.name}_${i}`;
        expect(content).toContain(`name="${name}"`);
        checkCount += 1;
      }
    }
    if (checkCount > 0) expect(content).toContain("sa.CheckConstraint(");
  });

  it.each([
    "ecommerce.spec",
    "todo_list.spec",
  ])("every derived foreign key surfaces as ForeignKeyConstraint in %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const content = migrationContent(fixture);
    let fkCount = 0;
    for (const table of profiled.schema.tables) {
      for (const fk of table.foreignKeys) {
        const fkRef = `["${fk.column}"], ["${fk.refTable}.${fk.refColumn}"]`;
        expect(content).toContain(fkRef);
        expect(content).toContain(`ondelete="${fk.onDelete}"`);
        fkCount += 1;
      }
    }
    if (fkCount > 0) expect(content).toContain("sa.ForeignKeyConstraint(");
  });

  it.each([
    "url_shortener.spec",
    "ecommerce.spec",
  ])("every derived index surfaces as op.create_index in %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const content = migrationContent(fixture);
    for (const table of profiled.schema.tables) {
      for (const ix of table.indexes) {
        expect(content).toContain(`op.create_index("${ix.name}", "${table.name}",`);
        expect(content).toContain(`unique=${ix.unique ? "True" : "False"}`);
      }
    }
  });

  it.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
  ])("downgrade drops tables in reverse topological order for %s", (fixture) => {
    const profiled = profiledFrom(fixture);
    const migration = buildAlembicMigration(profiled.schema);
    const content = migrationContent(fixture);
    const downgrade = content.split("def downgrade()")[1] ?? "";
    const positions = migration.tablesReversed.map((t) => ({
      name: t.name,
      pos: downgrade.indexOf(`op.drop_table("${t.name}")`),
    }));
    for (const entry of positions) expect(entry.pos).toBeGreaterThan(-1);
    for (let i = 1; i < positions.length; i += 1) {
      expect(positions[i].pos).toBeGreaterThan(positions[i - 1].pos);
    }
  });
});

describe("001_initial_schema.py — PostgreSQL dialect import", () => {
  it("imports postgresql dialect for the ecommerce fixture (JSONB fields present)", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const hasJsonb = profiled.schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    expect(hasJsonb, "ecommerce fixture must have a JSONB column for this test to be meaningful").toBe(true);
    expect(migrationContent("ecommerce.spec")).toContain(
      "from sqlalchemy.dialects import postgresql",
    );
  });

  it("omits postgresql dialect for the url_shortener fixture (no JSONB)", () => {
    const profiled = profiledFrom("url_shortener.spec");
    const hasJsonb = profiled.schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    expect(hasJsonb, "url_shortener fixture must NOT have a JSONB column for this test to be meaningful").toBe(false);
    expect(migrationContent("url_shortener.spec")).not.toContain(
      "from sqlalchemy.dialects import postgresql",
    );
  });
});

describe("001_initial_schema.py — empty-schema fallback", () => {
  it("emits `pass` in upgrade/downgrade when the schema has no tables", () => {
    const content = emitEmptySchemaMigration();
    expect(content).toMatch(/def upgrade\(\) -> None:\s*\n\s+pass/);
    expect(content).toMatch(/def downgrade\(\) -> None:\s*\n\s+pass/);
  });

  const maybeIt = python3Available() ? it : it.skip;
  maybeIt("parses as valid Python when the schema is empty", () => {
    const proc = spawnSync(
      "python3",
      ["-c", "import ast, sys; ast.parse(sys.stdin.read())"],
      { input: emitEmptySchemaMigration(), encoding: "utf-8" },
    );
    if (proc.status !== 0) {
      throw new Error(`empty-schema migration failed ast.parse:\n${proc.stderr}`);
    }
  });
});

function emitEmptySchemaMigration(): string {
  const profiled = profiledFrom("url_shortener.spec");
  const stripped: ProfiledService = {
    ...profiled,
    schema: { tables: [] },
    entities: [],
  };
  const files = emitProject(stripped);
  const file = files.find((f) =>
    f.path === "alembic/versions/001_initial_schema.py",
  );
  expect(file).toBeDefined();
  return file!.content;
}

describe("001_initial_schema.py — Python syntactic validity", () => {
  const maybeIt = python3Available() ? it : it.skip;
  maybeIt.each([
    "url_shortener.spec",
    "todo_list.spec",
    "ecommerce.spec",
    "auth_service.spec",
    "edge_cases.spec",
  ])("parses as valid Python via ast.parse (%s)", (fixture) => {
    const content = migrationContent(fixture);
    const proc = spawnSync(
      "python3",
      ["-c", "import ast, sys; ast.parse(sys.stdin.read())"],
      { input: content, encoding: "utf-8" },
    );
    if (proc.status !== 0) {
      throw new Error(`Migration for ${fixture} failed ast.parse:\n${proc.stderr}\n--- content ---\n${content}`);
    }
  });
});
