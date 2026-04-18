import type { ProfiledOperation } from "#profile/types.js";

export type RouteKind =
  | "create"
  | "read"
  | "list"
  | "delete"
  | "redirect"
  | "other";

const NAVIGATIONAL_REDIRECT_STATUSES: ReadonlySet<number> = new Set([301, 302, 303, 307, 308]);

export function classifyRouteKind(op: ProfiledOperation): RouteKind {
  const method = op.endpoint.method;
  const status = op.endpoint.successStatus;
  const pathParamCount = op.endpoint.pathParams.length;
  const hasPathParam = pathParamCount > 0;
  const singlePathParam = pathParamCount === 1;
  if (NAVIGATIONAL_REDIRECT_STATUSES.has(status)) return "redirect";
  if (op.kind === "create") return "create";
  if (op.kind === "read" && singlePathParam) return "read";
  if (op.kind === "read" && !hasPathParam) return "list";
  if (op.kind === "filtered_read" && !hasPathParam) return "list";
  if (op.kind === "delete" && singlePathParam) return "delete";
  if (method === "GET" && !hasPathParam) return "list";
  return "other";
}
