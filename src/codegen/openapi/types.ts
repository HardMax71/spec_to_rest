export type OpenApiSchemaType =
  | "string"
  | "integer"
  | "number"
  | "boolean"
  | "array"
  | "object"
  | "null";

export interface SchemaObject {
  readonly type?: OpenApiSchemaType | readonly OpenApiSchemaType[];
  readonly format?: string;
  readonly minLength?: number;
  readonly maxLength?: number;
  readonly minimum?: number;
  readonly maximum?: number;
  readonly exclusiveMinimum?: number;
  readonly exclusiveMaximum?: number;
  readonly minItems?: number;
  readonly maxItems?: number;
  readonly pattern?: string;
  readonly enum?: readonly string[];
  readonly items?: SchemaObject;
  readonly $ref?: string;
  readonly required?: readonly string[];
  readonly properties?: Readonly<Record<string, SchemaObject>>;
  readonly additionalProperties?: SchemaObject | boolean;
  readonly anyOf?: readonly SchemaObject[];
  readonly description?: string;
}

export interface ParameterObject {
  readonly name: string;
  readonly in: "path" | "query" | "header" | "cookie";
  readonly required: boolean;
  readonly description?: string;
  readonly schema: SchemaObject;
}

export interface MediaTypeObject {
  readonly schema: SchemaObject;
}

export interface RequestBodyObject {
  readonly required: boolean;
  readonly description?: string;
  readonly content: Readonly<Record<string, MediaTypeObject>>;
}

export interface HeaderObject {
  readonly description?: string;
  readonly schema: SchemaObject;
}

export interface ResponseObject {
  readonly description: string;
  readonly headers?: Readonly<Record<string, HeaderObject>>;
  readonly content?: Readonly<Record<string, MediaTypeObject>>;
}

export type ResponsesObject = Readonly<Record<string, ResponseObject>>;

export interface OperationObject {
  readonly operationId: string;
  readonly summary?: string;
  readonly description?: string;
  readonly tags: readonly string[];
  readonly parameters?: readonly ParameterObject[];
  readonly requestBody?: RequestBodyObject;
  readonly responses: ResponsesObject;
}

export interface PathItemObject {
  readonly get?: OperationObject;
  readonly post?: OperationObject;
  readonly put?: OperationObject;
  readonly patch?: OperationObject;
  readonly delete?: OperationObject;
}

export interface ComponentsObject {
  readonly schemas: Readonly<Record<string, SchemaObject>>;
}

export interface InfoObject {
  readonly title: string;
  readonly version: string;
  readonly description?: string;
}

export interface ServerObject {
  readonly url: string;
  readonly description?: string;
}

export interface TagObject {
  readonly name: string;
  readonly description?: string;
}

export interface OpenApiDocument {
  readonly openapi: "3.1.0";
  readonly info: InfoObject;
  readonly servers: readonly ServerObject[];
  readonly paths: Readonly<Record<string, PathItemObject>>;
  readonly components: ComponentsObject;
  readonly tags: readonly TagObject[];
}
