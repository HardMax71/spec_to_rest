import { toSnakeCase } from "#convention/naming.js";
import type { EndpointSpec, DatabaseSchema } from "#convention/types.js";
import type {
  ProfiledService,
  ProfiledEntity,
  ProfiledOperation,
  DeploymentProfile,
  TypeMapping,
} from "#profile/types.js";

export interface TypeMappingEntry {
  readonly specType: string;
  readonly python: string;
  readonly pydantic: string;
  readonly sqlalchemyColumn: string;
}

export interface RenderProfile {
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
  readonly pythonVersion: string;
  readonly modelDir: string;
  readonly schemaDir: string;
  readonly routerDir: string;
  readonly directories: readonly string[];
  readonly typeMap: readonly TypeMappingEntry[];
  readonly dependencies: DeploymentProfile["dependencies"];
  readonly devDependencies: DeploymentProfile["devDependencies"];
}

export interface RenderContext {
  readonly service: { readonly name: string; readonly snakeName: string };
  readonly profile: RenderProfile;
  readonly entities: readonly ProfiledEntity[];
  readonly operations: readonly ProfiledOperation[];
  readonly endpoints: readonly EndpointSpec[];
  readonly schema: DatabaseSchema;
}

export interface RenderResult {
  readonly fileName: string;
  readonly content: string;
}

export interface TemplateSource {
  readonly name: string;
  readonly content: string;
}

function convertTypeMap(typeMap: ReadonlyMap<string, TypeMapping>): readonly TypeMappingEntry[] {
  return [...typeMap.entries()].map(([specType, mapping]) => ({
    specType,
    python: mapping.python,
    pydantic: mapping.pydantic,
    sqlalchemyColumn: mapping.sqlalchemyColumn,
  }));
}

function convertProfile(profile: DeploymentProfile): RenderProfile {
  return {
    name: profile.name,
    displayName: profile.displayName,
    language: profile.language,
    framework: profile.framework,
    database: profile.database,
    orm: profile.orm,
    migrationTool: profile.migrationTool,
    validation: profile.validation,
    packageManager: profile.packageManager,
    httpServer: profile.httpServer,
    dbDriver: profile.dbDriver,
    async: profile.async,
    pythonVersion: profile.pythonVersion,
    modelDir: profile.modelDir,
    schemaDir: profile.schemaDir,
    routerDir: profile.routerDir,
    directories: profile.directories,
    typeMap: convertTypeMap(profile.typeMap),
    dependencies: profile.dependencies,
    devDependencies: profile.devDependencies,
  };
}

export function buildRenderContext(profiled: ProfiledService): RenderContext {
  return {
    service: {
      name: profiled.ir.name,
      snakeName: toSnakeCase(profiled.ir.name),
    },
    profile: convertProfile(profiled.profile),
    entities: profiled.entities,
    operations: profiled.operations,
    endpoints: profiled.endpoints,
    schema: profiled.schema,
  };
}
