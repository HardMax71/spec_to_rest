import { createOpenAPI } from "fumadocs-openapi/server";
import { createAPIPage } from "fumadocs-openapi/ui";
import { defaultShikiFactory } from "fumadocs-core/highlight/shiki/full";

export const openapi = createOpenAPI({
  input: ["public/openapi/url_shortener.yaml"],
});

export const APIPage = createAPIPage(openapi, {
  shiki: defaultShikiFactory,
  shikiOptions: {
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
  },
});
