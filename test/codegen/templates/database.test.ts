import { describe, it, expect } from "vitest";
import { TemplateEngine } from "#codegen/engine.js";
import { pythonFastapiPostgresTemplates } from "#codegen/templates.js";

describe("database.py template", () => {
  const engine = new TemplateEngine();
  const out = engine.render(pythonFastapiPostgresTemplates.database, {});

  it("creates an async engine reading settings.database_url", () => {
    expect(out).toContain("create_async_engine(");
    expect(out).toContain("settings.database_url");
  });

  it("exports get_session with commit/rollback lifecycle", () => {
    expect(out).toContain("async def get_session() -> AsyncIterator[AsyncSession]:");
    expect(out).toContain("await session.commit()");
    expect(out).toContain("await session.rollback()");
  });

  it("declares async_session_factory from async_sessionmaker", () => {
    expect(out).toContain("async_session_factory = async_sessionmaker(");
  });
});
