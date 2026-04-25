import { createRehypeCode } from "fumadocs-core/mdx-plugins/rehype-code.core";
import { createShikiFactory } from "fumadocs-core/highlight/shiki";
import type { LanguageRegistration } from "shiki";

export function createCustomRehypeCode(customLangs: LanguageRegistration[]) {
  const factory = createShikiFactory({
    async init(options) {
      const { createHighlighter, createJavaScriptRegexEngine } = await import("shiki");
      return createHighlighter({
        langs: customLangs,
        themes: [],
        langAlias: (options as { langAlias?: Record<string, string> } | undefined)?.langAlias,
        engine: createJavaScriptRegexEngine(),
      });
    },
  });

  return createRehypeCode(async (options: { langAlias?: Record<string, string> } | undefined) => {
    const highlighter = options?.langAlias
      ? await factory.init({ langAlias: options.langAlias })
      : await factory.getOrInit();
    return { highlighter, options: (options ?? {}) as never };
  });
}
