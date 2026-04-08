import Handlebars from "handlebars";
import {
  toSnakeCase,
  toKebabCase,
  splitCamelCase,
  pluralize as conventionPluralize,
} from "#convention/naming.js";

export function snakeCaseHelper(value: string): string {
  return toSnakeCase(value);
}

export function camelCaseHelper(value: string): string {
  const parts = splitCamelCase(value);
  return parts
    .map((w, i) =>
      i === 0 ? w.toLowerCase() : w[0].toUpperCase() + w.slice(1).toLowerCase(),
    )
    .join("");
}

export function pascalCaseHelper(value: string): string {
  const parts = splitCamelCase(value);
  return parts.map((w) => w[0].toUpperCase() + w.slice(1).toLowerCase()).join("");
}

export function kebabCaseHelper(value: string): string {
  return toKebabCase(value);
}

export function pluralizeHelper(value: string): string {
  return conventionPluralize(value);
}

export function upperHelper(value: string): string {
  return value.toUpperCase();
}

export function lowerHelper(value: string): string {
  return value.toLowerCase();
}

export function concatHelper(...args: unknown[]): string {
  const parts = args.slice(0, -1);
  return parts.map(String).join("");
}

export function joinHelper(array: readonly unknown[], separator: string): string {
  if (!Array.isArray(array)) return String(array);
  return array.join(separator);
}

export function indentHelper(
  this: unknown,
  contentOrSpaces: unknown,
  maybeSpaces?: unknown,
): string {
  if (typeof contentOrSpaces === "number" || typeof contentOrSpaces === "string" && maybeSpaces && typeof (maybeSpaces as Record<string, unknown>).fn === "function") {
    const spaces = Number(contentOrSpaces);
    const options = maybeSpaces as Handlebars.HelperOptions;
    const content = options.fn(this);
    return indentString(content, spaces);
  }

  const content = String(contentOrSpaces);
  const spaces = Number(maybeSpaces);
  return indentString(content, spaces);
}

function indentString(content: string, spaces: number): string {
  const pad = " ".repeat(spaces);
  return content
    .split("\n")
    .map((line) => (line.trim() === "" ? "" : pad + line))
    .join("\n");
}

export function eqHelper(a: unknown, b: unknown): boolean {
  return a === b;
}

export function neHelper(a: unknown, b: unknown): boolean {
  return a !== b;
}

export function andHelper(...args: unknown[]): boolean {
  const values = args.slice(0, -1);
  return values.every(Boolean);
}

export function orHelper(...args: unknown[]): boolean {
  const values = args.slice(0, -1);
  return values.some(Boolean);
}

export function notHelper(value: unknown): boolean {
  return !value;
}

export function registerHelpers(hbs: typeof Handlebars): void {
  hbs.registerHelper("snake_case", (v: string) => snakeCaseHelper(v));
  hbs.registerHelper("camel_case", (v: string) => camelCaseHelper(v));
  hbs.registerHelper("pascal_case", (v: string) => pascalCaseHelper(v));
  hbs.registerHelper("kebab_case", (v: string) => kebabCaseHelper(v));
  hbs.registerHelper("pluralize", (v: string) => pluralizeHelper(v));
  hbs.registerHelper("upper", (v: string) => upperHelper(v));
  hbs.registerHelper("lower", (v: string) => lowerHelper(v));
  hbs.registerHelper("concat", concatHelper);
  hbs.registerHelper("join", joinHelper);
  hbs.registerHelper("indent", indentHelper);
  hbs.registerHelper("eq", eqHelper);
  hbs.registerHelper("ne", neHelper);
  hbs.registerHelper("and", andHelper);
  hbs.registerHelper("or", orHelper);
  hbs.registerHelper("not", notHelper);
}
