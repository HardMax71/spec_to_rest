package specrest.profile

import specrest.convention.DatabaseSchema
import specrest.convention.EndpointSpec
import specrest.convention.OperationKind
import specrest.ir.generated.SpecRestGenerated.*

enum NamingStyle derives CanEqual:
  case SnakeCase, PascalCase, CamelCase, KebabCase

final case class TypeMapping(
    domain: String,
    validation: String,
    ormColumn: String
)

final case class DependencySpec(name: String, version: String)

final case class DeploymentProfile(
    name: String,
    displayName: String,
    language: String,
    framework: String,
    database: String,
    orm: String,
    migrationTool: String,
    validation: String,
    packageManager: String,
    httpServer: String,
    dbDriver: String,
    async: Boolean,
    fileNaming: NamingStyle,
    classNaming: NamingStyle,
    fieldNaming: NamingStyle,
    typeMap: Map[String, TypeMapping],
    dependencies: List[DependencySpec],
    devDependencies: List[DependencySpec],
    pythonVersion: String,
    directories: List[String]
)

final case class MappedType(
    domain: String,
    validation: String,
    orm: String
)

final case class ProfiledField(
    fieldName: String,
    columnName: String,
    domainType: String,
    validationType: String,
    ormFieldType: String,
    ormColumnType: String,
    nullable: Boolean,
    hasDefault: Boolean
)

final case class ProfiledEntity(
    entityName: String,
    tableName: String,
    modelClassName: String,
    createSchemaName: String,
    readSchemaName: String,
    updateSchemaName: String,
    modelFileName: String,
    schemaFileName: String,
    routerFileName: String,
    fields: List[ProfiledField]
)

final case class ProfiledOperation(
    operationName: String,
    handlerName: String,
    endpoint: EndpointSpec,
    kind: OperationKind,
    targetEntity: Option[String],
    requestBodyFields: List[ProfiledField],
    responseFields: List[ProfiledField],
    dafnyMethod: Option[String] = None
)

final case class ProfiledService(
    ir: ServiceIRFull,
    profile: DeploymentProfile,
    endpoints: List[EndpointSpec],
    schema: DatabaseSchema,
    entities: List[ProfiledEntity],
    operations: List[ProfiledOperation]
)
