import Handlebars from "handlebars";
import {
  toSnakeCase,
  toKebabCase,
  splitCamelCase,
  pluralize as conventionPluralize,
} from "#convention/naming.js";

export type Primitive = string | number | boolean;

export function snakeCaseHelper(value: string): string {
  return toSnakeCase(value);
}

export function camelCaseHelper(value: string): string {
  const parts = splitCamelCase(value).filter((w) => w.length > 0);
  return parts
    .map((w, i) =>
      i === 0 ? w.toLowerCase() : w[0].toUpperCase() + w.slice(1).toLowerCase(),
    )
    .join("");
}

export function pascalCaseHelper(value: string): string {
  const parts = splitCamelCase(value).filter((w) => w.length > 0);
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

export function concatHelper(...parts: string[]): string {
  return parts.join("");
}

export function joinHelper(array: readonly string[], separator: string = ","): string {
  return array.join(separator);
}

export function indentString(content: string, spaces: number): string {
  const pad = " ".repeat(spaces);
  return content
    .split("\n")
    .map((line) => (line.trim() === "" ? "" : pad + line))
    .join("\n");
}

export function eqHelper(a: Primitive, b: Primitive): boolean {
  return a === b;
}

export function neHelper(a: Primitive, b: Primitive): boolean {
  return a !== b;
}

export function andHelper(a: Primitive, b: Primitive): boolean {
  return !!a && !!b;
}

export function orHelper(a: Primitive, b: Primitive): boolean {
  return !!a || !!b;
}

export function notHelper(value: Primitive): boolean {
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
  hbs.registerHelper("eq", (a: Primitive, b: Primitive) => eqHelper(a, b));
  hbs.registerHelper("ne", (a: Primitive, b: Primitive) => neHelper(a, b));
  hbs.registerHelper("and", (a: Primitive, b: Primitive) => andHelper(a, b));
  hbs.registerHelper("or", (a: Primitive, b: Primitive) => orHelper(a, b));
  hbs.registerHelper("not", (v: Primitive) => notHelper(v));

  const concatAdapter: Handlebars.HelperDelegate = (a, b, c, d, e) =>
    concatHelper(...[a, b, c, d, e].filter((s): s is string => typeof s === "string"));
  hbs.registerHelper("concat", concatAdapter);

  const joinAdapter: Handlebars.HelperDelegate = (array, separator) =>
    joinHelper(array, typeof separator === "string" ? separator : ",");
  hbs.registerHelper("join", joinAdapter);

  const indentAdapter: Handlebars.HelperDelegate = function (
    this: Record<string, string>,
    first,
    second,
  ) {
    if (second && typeof second.fn === "function") {
      return indentString(second.fn(this), Number(first));
    }
    return indentString(String(first), Number(second));
  };
  hbs.registerHelper("indent", indentAdapter);
}
