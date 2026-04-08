import type { DeploymentProfile, TypeMapping } from "#profile/types.js";

const TYPE_MAP = new Map<string, TypeMapping>([
  ["String",   { python: "str",         pydantic: "str",         sqlalchemy: "String"     }],
  ["Int",      { python: "int",         pydantic: "int",         sqlalchemy: "Integer"    }],
  ["Float",    { python: "float",       pydantic: "float",       sqlalchemy: "Float"      }],
  ["Bool",     { python: "bool",        pydantic: "bool",        sqlalchemy: "Boolean"    }],
  ["Boolean",  { python: "bool",        pydantic: "bool",        sqlalchemy: "Boolean"    }],
  ["DateTime", { python: "datetime",    pydantic: "datetime",    sqlalchemy: "DateTime"   }],
  ["Date",     { python: "date",        pydantic: "date",        sqlalchemy: "Date"       }],
  ["UUID",     { python: "uuid.UUID",   pydantic: "uuid.UUID",   sqlalchemy: "Uuid"       }],
  ["Decimal",  { python: "Decimal",     pydantic: "Decimal",     sqlalchemy: "Numeric"    }],
  ["Bytes",    { python: "bytes",       pydantic: "bytes",       sqlalchemy: "LargeBinary"}],
  ["Money",    { python: "int",         pydantic: "int",         sqlalchemy: "Integer"    }],
]);

export const PYTHON_FASTAPI_POSTGRES: DeploymentProfile = {
  name: "python-fastapi-postgres",
  displayName: "Python + FastAPI + PostgreSQL",
  language: "python",
  framework: "fastapi",
  database: "postgres",

  orm: "sqlalchemy",
  migrationTool: "alembic",
  validation: "pydantic",
  packageManager: "uv",
  httpServer: "uvicorn",
  dbDriver: "asyncpg",
  async: true,

  fileNaming: "snake_case",
  classNaming: "PascalCase",
  fieldNaming: "snake_case",

  typeMap: TYPE_MAP,

  dependencies: [
    { name: "fastapi", version: ">=0.115" },
    { name: "uvicorn[standard]", version: ">=0.34" },
    { name: "sqlalchemy", version: ">=2.0" },
    { name: "asyncpg", version: ">=0.30" },
    { name: "alembic", version: ">=1.14" },
    { name: "pydantic-settings", version: ">=2.0" },
  ],

  devDependencies: [
    { name: "pytest", version: ">=8.0" },
    { name: "pytest-asyncio", version: ">=0.24" },
    { name: "httpx", version: ">=0.28" },
    { name: "ruff", version: ">=0.8" },
    { name: "mypy", version: ">=1.13" },
  ],

  pythonVersion: ">=3.12",

  directories: [
    "app",
    "app/models",
    "app/schemas",
    "app/routers",
    "app/services",
    "alembic",
    "alembic/versions",
    "tests",
  ],
};
