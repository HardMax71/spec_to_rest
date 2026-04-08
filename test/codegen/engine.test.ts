import { describe, it, expect } from "vitest";
import { readFileSync } from "node:fs";
import { join } from "node:path";
import { parseSpec } from "#parser/index.js";
import { buildIR } from "#ir/index.js";
import { buildProfiledService } from "#profile/annotate.js";
import { TemplateEngine } from "#codegen/engine.js";
import { buildRenderContext } from "#codegen/types.js";
import type { RenderContext } from "#codegen/types.js";

const fixtureDir = join(import.meta.dirname, "../parser/fixtures");

function contextFrom(name: string): RenderContext {
  const src = readFileSync(join(fixtureDir, name), "utf-8");
  const { tree, errors } = parseSpec(src);
  expect(errors).toEqual([]);
  const ir = buildIR(tree);
  const profiled = buildProfiledService(ir, "python-fastapi-postgres");
  return buildRenderContext(profiled);
}

const SCHEMA_TEMPLATE = `from pydantic import BaseModel


class {{entity.createSchemaName}}(BaseModel):
{{#each entity.fields}}
    {{snake_case this.fieldName}}: {{this.pydanticType}}
{{/each}}


class {{entity.readSchemaName}}(BaseModel):
    id: int
{{#each entity.fields}}
    {{snake_case this.fieldName}}: {{this.pydanticType}}
{{/each}}

    model_config = {"from_attributes": True}
`;

describe("TemplateEngine", () => {
  it("renders simple variable substitution", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render("Service: {{service.name}}", ctx);
    expect(result).toBe("Service: UrlShortener");
  });

  it("renders snake_case service name", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render("{{service.snakeName}}", ctx);
    expect(result).toBe("url_shortener");
  });

  it("renders profile fields", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render("{{profile.framework}} + {{profile.orm}}", ctx);
    expect(result).toBe("fastapi + sqlalchemy");
  });

  it("iterates over entities", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render(
      "{{#each entities}}{{this.entityName}} {{/each}}",
      ctx,
    );
    expect(result).toContain("UrlMapping");
  });

  it("iterates over operations with helpers", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render(
      "{{#each operations}}{{snake_case this.operationName}}\n{{/each}}",
      ctx,
    );
    expect(result).toContain("shorten");
    expect(result).toContain("resolve");
    expect(result).toContain("delete");
  });

  it("uses eq helper in conditional", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const result = engine.render(
      '{{#each operations}}{{#if (eq this.endpoint.method "POST")}}{{this.operationName}} {{/if}}{{/each}}',
      ctx,
    );
    expect(result).toContain("Shorten");
  });
});

describe("TemplateEngine — PoC schema rendering", () => {
  it("renders valid Python schema for url_shortener", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const entity = ctx.entities[0];
    const result = engine.render(SCHEMA_TEMPLATE, { ...ctx, entity });
    expect(result).toContain("from pydantic import BaseModel");
    expect(result).toContain("class UrlMappingCreate(BaseModel):");
    expect(result).toContain("class UrlMappingRead(BaseModel):");
    expect(result).toContain('model_config = {"from_attributes": True}');
  });

  it("renders fields with snake_case names and Python types", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const entity = ctx.entities[0];
    const result = engine.render(SCHEMA_TEMPLATE, { ...ctx, entity });
    for (const field of entity.fields) {
      expect(result).toContain(`${field.columnName}: ${field.pydanticType}`);
    }
  });

  it("renders consistent 4-space indentation", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("url_shortener.spec");
    const entity = ctx.entities[0];
    const result = engine.render(SCHEMA_TEMPLATE, { ...ctx, entity });
    const indentedLines = result.split("\n").filter((l) => l.startsWith(" "));
    for (const line of indentedLines) {
      const leadingSpaces = line.match(/^( +)/)![1].length;
      expect(leadingSpaces % 4).toBe(0);
    }
  });

  it("renders todo_list schema with nullable fields", () => {
    const engine = new TemplateEngine();
    const ctx = contextFrom("todo_list.spec");
    const todo = ctx.entities.find((e) => e.entityName === "Todo")!;
    const result = engine.render(SCHEMA_TEMPLATE, { ...ctx, entity: todo });
    expect(result).toContain("class TodoCreate(BaseModel):");
    const description = todo.fields.find((f) => f.fieldName === "description");
    if (description?.nullable) {
      expect(result).toContain("| None");
    }
  });
});

describe("TemplateEngine — partials", () => {
  it("registers and renders a partial", () => {
    const engine = new TemplateEngine();
    engine.registerPartial(
      "field_line",
      "    {{snake_case fieldName}}: {{pydanticType}}",
    );
    const ctx = contextFrom("url_shortener.spec");
    const field = ctx.entities[0].fields[0];
    const result = engine.render("{{> field_line}}", field as never);
    expect(result).toContain(`: ${field.pydanticType}`);
  });
});

describe("TemplateEngine — compileTemplate", () => {
  it("compiles template for reuse with different contexts", () => {
    const engine = new TemplateEngine();
    const compiled = engine.compileTemplate("Entity: {{entityName}}");
    const ctx = contextFrom("ecommerce.spec");
    const results = ctx.entities.map((e) => compiled(e));
    expect(results.length).toBeGreaterThan(1);
    for (const [i, result] of results.entries()) {
      expect(result).toBe(`Entity: ${ctx.entities[i].entityName}`);
    }
  });
});

describe("buildRenderContext", () => {
  it("converts profile typeMap to array", () => {
    const ctx = contextFrom("url_shortener.spec");
    expect(Array.isArray(ctx.profile.typeMap)).toBe(true);
    expect(ctx.profile.typeMap.length).toBeGreaterThan(0);
    const strEntry = ctx.profile.typeMap.find((t) => t.specType === "String");
    expect(strEntry).toBeDefined();
    expect(strEntry!.python).toBe("str");
  });

  it("populates service snakeName", () => {
    const ctx = contextFrom("url_shortener.spec");
    expect(ctx.service.name).toBe("UrlShortener");
    expect(ctx.service.snakeName).toBe("url_shortener");
  });

  it("preserves all entities and operations", () => {
    const ctx = contextFrom("ecommerce.spec");
    expect(ctx.entities.length).toBe(5);
    expect(ctx.operations.length).toBe(11);
  });
});
