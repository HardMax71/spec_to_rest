import yaml from "js-yaml";
import type { OpenApiDocument } from "#codegen/openapi/types.js";

export function serializeOpenApi(doc: OpenApiDocument): string {
  return yaml.dump(doc, {
    lineWidth: 100,
    noRefs: true,
    sortKeys: false,
  });
}
