import type { ProfiledOperation } from "#profile/types.js";

export type RouteKind =
  | "create"
  | "read"
  | "list"
  | "delete"
  | "other";

export function classifyRouteKind(op: ProfiledOperation): RouteKind {
  const method = op.endpoint.method;
  const pathParamCount = op.endpoint.pathParams.length;
  const hasPathParam = pathParamCount > 0;
  const singlePathParam = pathParamCount === 1;
  if (op.kind === "create") return "create";
  if (op.kind === "read" && singlePathParam) return "read";
  if (op.kind === "read" && !hasPathParam) return "list";
  if (op.kind === "filtered_read" && !hasPathParam) return "list";
  if (op.kind === "delete" && singlePathParam) return "delete";
  if (method === "GET" && !hasPathParam) return "list";
  return "other";
}
