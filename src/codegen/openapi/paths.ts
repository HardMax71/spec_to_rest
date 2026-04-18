import type { BuildContext } from "#codegen/openapi/components.js";
import { fieldToSchema } from "#codegen/openapi/schema.js";
import type {
  MediaTypeObject,
  OperationObject,
  ParameterObject,
  PathItemObject,
  RequestBodyObject,
  ResponseObject,
  ResponsesObject,
  SchemaObject,
} from "#codegen/openapi/types.js";
import { classifyRouteKind, type RouteKind } from "#codegen/route-kind.js";
import { toSnakeCase } from "#convention/naming.js";
import type { HttpMethod, ParamSpec } from "#convention/types.js";
import type {
  ProfiledEntity,
  ProfiledOperation,
  ProfiledService,
} from "#profile/types.js";

export function buildPaths(
  profiled: ProfiledService,
  ctx: BuildContext,
): Readonly<Record<string, PathItemObject>> {
  const paths: Record<string, PathItemObject> = {};

  const entityByName = new Map(
    profiled.entities.map((e) => [e.entityName, e] as const),
  );

  for (const op of profiled.operations) {
    const entity = op.targetEntity ? entityByName.get(op.targetEntity) : undefined;
    const operation = buildOperation(op, entity, ctx);
    const current = paths[op.endpoint.path] ?? {};
    paths[op.endpoint.path] = {
      ...current,
      [methodKey(op.endpoint.method)]: operation,
    };
  }

  paths["/health"] = {
    get: {
      operationId: "health_check",
      summary: "Health check",
      description: "Returns 200 if the service is running.",
      tags: ["infrastructure"],
      responses: {
        "200": {
          description: "Service is healthy",
          content: {
            "application/json": {
              schema: {
                type: "object",
                required: ["status"],
                properties: { status: { type: "string", enum: ["ok"] } },
              },
            },
          },
        },
      },
    },
  };

  return paths;
}

function methodKey(method: HttpMethod): keyof PathItemObject {
  return method.toLowerCase() as keyof PathItemObject;
}

function buildOperation(
  op: ProfiledOperation,
  entity: ProfiledEntity | undefined,
  ctx: BuildContext,
): OperationObject {
  const routeKind = classifyRouteKind(op);
  const parameters = buildParameters(op, ctx);
  const requestBody = buildRequestBody(op, entity, routeKind, ctx);
  const responses = buildResponses(op, entity, routeKind);

  const operation: OperationObject = {
    operationId: op.handlerName,
    summary: op.operationName,
    tags: [operationTag(op)],
    responses,
    ...(parameters.length > 0 ? { parameters } : {}),
    ...(requestBody !== null ? { requestBody } : {}),
  };
  return operation;
}

function operationTag(op: ProfiledOperation): string {
  if (op.targetEntity === null) return "infrastructure";
  return toSnakeCase(op.targetEntity);
}

function buildParameters(
  op: ProfiledOperation,
  ctx: BuildContext,
): readonly ParameterObject[] {
  const out: ParameterObject[] = [];
  for (const p of op.endpoint.pathParams) {
    out.push(paramObject(p, "path", ctx));
  }
  for (const p of op.endpoint.queryParams) {
    out.push(paramObject(p, "query", ctx));
  }
  return out;
}

function paramObject(
  p: ParamSpec,
  location: "path" | "query",
  ctx: BuildContext,
): ParameterObject {
  const { schema } = fieldToSchema({
    typeExpr: p.typeExpr,
    aliasMap: ctx.aliasMap,
    enumMap: ctx.enumMap,
    entityNames: ctx.entityNames,
  });
  return {
    name: p.name,
    in: location,
    required: location === "path" ? true : p.required,
    schema,
  };
}

function buildRequestBody(
  op: ProfiledOperation,
  entity: ProfiledEntity | undefined,
  routeKind: RouteKind,
  ctx: BuildContext,
): RequestBodyObject | null {
  if (op.endpoint.method === "GET" || op.endpoint.method === "DELETE") {
    return null;
  }
  if (op.endpoint.bodyParams.length === 0) {
    return null;
  }

  if (entity !== undefined && (routeKind === "create" || op.kind === "create")) {
    return componentBody(entity.createSchemaName);
  }
  if (entity !== undefined && (op.kind === "replace" || op.kind === "partial_update")) {
    return componentBody(entity.updateSchemaName);
  }

  const inline = inlineBodySchema(op, ctx);
  if (inline === null) return null;
  return {
    required: true,
    content: { "application/json": { schema: inline } },
  };
}

function componentBody(schemaName: string): RequestBodyObject {
  return {
    required: true,
    content: {
      "application/json": {
        schema: { $ref: `#/components/schemas/${schemaName}` },
      },
    },
  };
}

function inlineBodySchema(
  op: ProfiledOperation,
  ctx: BuildContext,
): SchemaObject | null {
  const params = op.endpoint.bodyParams;
  if (params.length === 0) return null;

  const properties: Record<string, SchemaObject> = {};
  const required: string[] = [];
  for (const p of params) {
    const { schema, nullable } = fieldToSchema({
      typeExpr: p.typeExpr,
      aliasMap: ctx.aliasMap,
      enumMap: ctx.enumMap,
      entityNames: ctx.entityNames,
    });
    properties[p.name] = schema;
    if (p.required && !nullable) required.push(p.name);
  }
  return {
    type: "object",
    properties,
    ...(required.length > 0 ? { required } : {}),
  };
}

function buildResponses(
  op: ProfiledOperation,
  entity: ProfiledEntity | undefined,
  routeKind: RouteKind,
): ResponsesObject {
  const status = String(op.endpoint.successStatus);
  const success = buildSuccessResponse(op, entity, routeKind);
  const responses: Record<string, ResponseObject> = { [status]: success };

  const hasPathParam = op.endpoint.pathParams.length > 0;
  const needs404 =
    hasPathParam &&
    (op.kind === "read" ||
      op.kind === "delete" ||
      op.kind === "replace" ||
      op.kind === "partial_update" ||
      op.kind === "transition" ||
      op.kind === "create_child");
  if (needs404) {
    responses["404"] = errorResponseRef("Resource not found");
  }

  const acceptsInput =
    op.endpoint.bodyParams.length > 0 ||
    op.endpoint.queryParams.length > 0 ||
    op.endpoint.pathParams.length > 0;
  if (acceptsInput) {
    responses["422"] = errorResponseRef("Validation error");
  }

  return responses;
}

function buildSuccessResponse(
  op: ProfiledOperation,
  entity: ProfiledEntity | undefined,
  routeKind: RouteKind,
): ResponseObject {
  if (op.endpoint.successStatus === 204) {
    return { description: "No content" };
  }
  if (op.endpoint.successStatus >= 300 && op.endpoint.successStatus < 400) {
    return {
      description: "Redirect",
      headers: {
        Location: {
          description: "Target URL",
          schema: { type: "string", format: "uri" },
        },
      },
    };
  }

  if (entity !== undefined) {
    if (routeKind === "list") {
      return jsonResponse("Successful response", {
        type: "array",
        items: { $ref: `#/components/schemas/${entity.readSchemaName}` },
      });
    }
    if (routeKind === "create" || routeKind === "read") {
      return jsonResponse("Successful response", {
        $ref: `#/components/schemas/${entity.readSchemaName}`,
      });
    }
    if (op.kind === "replace" || op.kind === "partial_update") {
      return jsonResponse("Successful response", {
        $ref: `#/components/schemas/${entity.readSchemaName}`,
      });
    }
  }

  return { description: "Successful response" };
}

function jsonResponse(description: string, schema: SchemaObject): ResponseObject {
  return {
    description,
    content: { "application/json": { schema } },
  };
}

function errorResponseRef(description: string): ResponseObject {
  const media: MediaTypeObject = {
    schema: { $ref: "#/components/schemas/ErrorResponse" },
  };
  return {
    description,
    content: { "application/json": media },
  };
}
