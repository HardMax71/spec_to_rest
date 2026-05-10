package specrest.profile

object TsExpressPostgres:

  private val PrimitiveTypeMap: Map[String, TypeMapping] = Map(
    "String"   -> TypeMapping("string", "string", "TEXT"),
    "Int"      -> TypeMapping("number", "number", "INTEGER"),
    "Float"    -> TypeMapping("number", "number", "DOUBLE PRECISION"),
    "Bool"     -> TypeMapping("boolean", "boolean", "BOOLEAN"),
    "Boolean"  -> TypeMapping("boolean", "boolean", "BOOLEAN"),
    "DateTime" -> TypeMapping("Date", "Date", "TIMESTAMPTZ"),
    "Date"     -> TypeMapping("Date", "Date", "DATE"),
    "UUID"     -> TypeMapping("string", "string", "UUID"),
    "Decimal"  -> TypeMapping("Prisma.Decimal", "Prisma.Decimal", "DECIMAL"),
    "Bytes"    -> TypeMapping("Buffer", "Buffer", "BYTEA"),
    "Money"    -> TypeMapping("number", "number", "INTEGER")
  )

  val profile: DeploymentProfile = DeploymentProfile(
    name = "ts-express-postgres",
    displayName = "TypeScript + Express + Prisma + PostgreSQL",
    language = "ts",
    framework = "express",
    database = "postgres",
    orm = "prisma",
    migrationTool = "prisma-migrate",
    validation = "zod",
    packageManager = "npm",
    httpServer = "express",
    dbDriver = "@prisma/client",
    async = true,
    fileNaming = NamingStyle.CamelCase,
    classNaming = NamingStyle.PascalCase,
    fieldNaming = NamingStyle.CamelCase,
    typeMap = PrimitiveTypeMap,
    dependencies = List(
      DependencySpec("express", "^4.21.2"),
      DependencySpec("@prisma/client", "^5.22.0"),
      DependencySpec("zod", "^3.23.8"),
      DependencySpec("dotenv", "^16.4.7")
    ),
    devDependencies = List(
      DependencySpec("typescript", "^5.6.3"),
      DependencySpec("@types/node", "^22.10.1"),
      DependencySpec("@types/express", "^4.17.21"),
      DependencySpec("prisma", "^5.22.0"),
      DependencySpec("vitest", "^2.1.8"),
      DependencySpec("supertest", "^7.0.0"),
      DependencySpec("@types/supertest", "^6.0.2"),
      DependencySpec("tsx", "^4.19.2")
    ),
    pythonVersion = "",
    directories = List(
      "src",
      "src/middleware",
      "src/routes",
      "src/services",
      "src/schemas",
      "src/types",
      "prisma",
      "tests"
    )
  )
