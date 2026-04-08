import type { ServiceIR } from "#ir/types.js";
import type { EndpointSpec, DatabaseSchema, OperationKind } from "#convention/types.js";

export type NamingStyle = "snake_case" | "PascalCase" | "camelCase" | "kebab-case";

export interface TypeMapping {
  readonly python: string;
  readonly pydantic: string;
  readonly sqlalchemyColumn: string;
}

export interface DependencySpec {
  readonly name: string;
  readonly version: string;
}

export interface DeploymentProfile {
  readonly name: string;
  readonly displayName: string;
  readonly language: string;
  readonly framework: string;
  readonly database: string;

  readonly orm: string;
  readonly migrationTool: string;
  readonly validation: string;
  readonly packageManager: string;
  readonly httpServer: string;
  readonly dbDriver: string;
  readonly async: boolean;

  readonly fileNaming: NamingStyle;
  readonly classNaming: NamingStyle;
  readonly fieldNaming: NamingStyle;

  readonly typeMap: ReadonlyMap<string, TypeMapping>;
  readonly dependencies: readonly DependencySpec[];
  readonly devDependencies: readonly DependencySpec[];
  readonly pythonVersion: string;

  readonly directories: readonly string[];

  readonly modelDir: string;
  readonly schemaDir: string;
  readonly routerDir: string;
}

export interface MappedType {
  readonly python: string;
  readonly pydantic: string;
  readonly sqlalchemy: string;
}

export interface ProfiledField {
  readonly fieldName: string;
  readonly columnName: string;
  readonly pythonType: string;
  readonly pydanticType: string;
  readonly sqlalchemyType: string;
  readonly sqlalchemyColumnType: string;
  readonly nullable: boolean;
  readonly hasDefault: boolean;
}

export interface ProfiledEntity {
  readonly entityName: string;
  readonly tableName: string;
  readonly modelClassName: string;
  readonly createSchemaName: string;
  readonly readSchemaName: string;
  readonly updateSchemaName: string;
  readonly modelFileName: string;
  readonly schemaFileName: string;
  readonly routerFileName: string;
  readonly fields: readonly ProfiledField[];
}

export interface ProfiledOperation {
  readonly operationName: string;
  readonly handlerName: string;
  readonly endpoint: EndpointSpec;
  readonly kind: OperationKind;
  readonly targetEntity: string | null;
  readonly requestBodyFields: readonly ProfiledField[];
  readonly responseFields: readonly ProfiledField[];
}

export interface ProfiledService {
  readonly ir: ServiceIR;
  readonly profile: DeploymentProfile;
  readonly endpoints: readonly EndpointSpec[];
  readonly schema: DatabaseSchema;
  readonly entities: readonly ProfiledEntity[];
  readonly operations: readonly ProfiledOperation[];
}
