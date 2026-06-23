import { createOpenAPI } from "fumadocs-openapi/server";
import type { OpenAPIV3_2, OperationItem } from "fumadocs-openapi";
import { OpenAPIPage } from "./openapi-page";

const SCHEMA_ID = "public/openapi/url_shortener.yaml";

const HTTP_METHODS = [
  "get",
  "put",
  "post",
  "delete",
  "options",
  "head",
  "patch",
  "trace",
] as const satisfies readonly OpenAPIV3_2.HttpMethods[];

export const openapi = createOpenAPI({
  input: [SCHEMA_ID],
});

export async function APIPage(props: {
  document?: string;
  operations?: OperationItem[];
}) {
  const { bundled } = await openapi.getSchema(props.document ?? SCHEMA_ID);
  const operations =
    props.operations ??
    Object.entries(bundled.paths ?? {}).flatMap(([path, item]) =>
      HTTP_METHODS.filter((method) => item?.[method] != null).map((method) => ({
        path,
        method,
      })),
    );
  return <OpenAPIPage payload={{ bundled }} operations={operations} />;
}
