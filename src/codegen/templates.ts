import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

const templateRoot = join(
  dirname(fileURLToPath(import.meta.url)),
  "templates",
  "python-fastapi-postgres",
);

function loadTemplate(relPath: string): string {
  return readFileSync(join(templateRoot, relPath), "utf-8");
}

export const pythonFastapiPostgresTemplates = Object.freeze({
  main: loadTemplate("main.py.hbs"),
  config: loadTemplate("config.py.hbs"),
  database: loadTemplate("database.py.hbs"),
  dbBase: loadTemplate("db/base.py.hbs"),
  modelEntity: loadTemplate("models/entity.py.hbs"),
  modelInit: loadTemplate("models/__init__.py.hbs"),
  schemaEntity: loadTemplate("schemas/entity.py.hbs"),
  schemaInit: loadTemplate("schemas/__init__.py.hbs"),
  routerEntity: loadTemplate("routers/entity.py.hbs"),
  routerInit: loadTemplate("routers/__init__.py.hbs"),
  serviceEntity: loadTemplate("services/entity.py.hbs"),
  serviceInit: loadTemplate("services/__init__.py.hbs"),
  alembicIni: loadTemplate("alembic.ini.hbs"),
  alembicEnv: loadTemplate("alembic/env.py.hbs"),
  alembicMigration: loadTemplate("alembic/versions/001_initial_schema.py.hbs"),
  pyproject: loadTemplate("pyproject.toml.hbs"),
  dockerfile: loadTemplate("Dockerfile.hbs"),
  dockerCompose: loadTemplate("docker-compose.yml.hbs"),
  envExample: loadTemplate(".env.example.hbs"),
  makefile: loadTemplate("Makefile.hbs"),
  gitignore: loadTemplate(".gitignore.hbs"),
  dockerignore: loadTemplate(".dockerignore.hbs"),
  readme: loadTemplate("README.md.hbs"),
  ciWorkflow: loadTemplate(".github/workflows/ci.yml.hbs"),
  testHealth: loadTemplate("tests/test_health.py.hbs"),
});

export type PythonFastapiPostgresTemplates = typeof pythonFastapiPostgresTemplates;
