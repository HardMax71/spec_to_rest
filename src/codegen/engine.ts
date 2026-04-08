import Handlebars from "handlebars";
import { registerHelpers } from "#codegen/helpers.js";
import type { RenderContext } from "#codegen/types.js";

export class TemplateEngine {
  private readonly hbs: typeof Handlebars;

  constructor() {
    this.hbs = Handlebars.create();
    registerHelpers(this.hbs);
  }

  render(templateSource: string, context: RenderContext): string {
    const compiled = this.hbs.compile(templateSource, { noEscape: true });
    return compiled(context);
  }

  compileTemplate(source: string): HandlebarsTemplateDelegate {
    return this.hbs.compile(source, { noEscape: true });
  }

  registerPartial(name: string, source: string): void {
    this.hbs.registerPartial(name, source);
  }
}
