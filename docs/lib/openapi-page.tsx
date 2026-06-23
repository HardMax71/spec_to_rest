"use client";

import { createOpenAPIPage } from "fumadocs-openapi/ui";

export const OpenAPIPage = createOpenAPIPage({
  shikiOptions: {
    themes: { light: "github-light", dark: "github-dark" },
    defaultColor: false,
  },
  generateTypeScriptDefinitions: false,
  showResponseSchema: true,
});
