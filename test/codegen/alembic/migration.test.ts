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
        if (targetPos === undefined) continue;
        expect(targetPos).toBeLessThan(position.get(table.name)!);
      }
    }
  });

  it("tablesReversed reverses tables (for downgrade drop order)", () => {
    const profiled = profiledFrom("ecommerce.spec");
    const migration = buildAlembicMigration(profiled.schema);
    expect(migration.tablesReversed.map((t) => t.name)).toEqual(
      [...migration.tables].reverse().map((t) => t.name),
    );
  });

  it("sets needsPostgresDialect only when a JSONB column exists", () => {
    const withJsonb = profiledFrom("ecommerce.spec");
    const hasJsonbColumn = withJsonb.schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    expect(buildAlembicMigration(withJsonb.schema).needsPostgresDialect).toBe(
      hasJsonbColumn,
    );

    const noJsonb = profiledFrom("url_shortener.spec");
    const urlShortenerHasJsonb = noJsonb.schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    expect(buildAlembicMigration(noJsonb.schema).needsPostgresDialect).toBe(
      urlShortenerHasJsonb,
    );
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
  it("imports the postgresql dialect when any JSONB column is emitted", () => {
    const content = migrationContent("ecommerce.spec");
    const hasJsonb = profiledFrom("ecommerce.spec").schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    if (hasJsonb) {
      expect(content).toContain("from sqlalchemy.dialects import postgresql");
    }
  });

  it("omits the postgresql import when no JSONB column exists", () => {
    const content = migrationContent("url_shortener.spec");
    const hasJsonb = profiledFrom("url_shortener.spec").schema.tables.some((t) =>
      t.columns.some((c) => c.sqlType === "JSONB"),
    );
    if (!hasJsonb) {
      expect(content).not.toContain("from sqlalchemy.dialects import postgresql");
    }
  });
});

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
