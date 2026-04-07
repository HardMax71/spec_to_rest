const UNCOUNTABLE = new Set([
  "data",
  "info",
  "information",
  "metadata",
  "inventory",
  "feedback",
  "equipment",
  "software",
  "hardware",
  "middleware",
  "auth",
]);

const IRREGULAR: ReadonlyMap<string, string> = new Map([
  ["person", "people"],
  ["child", "children"],
  ["man", "men"],
  ["woman", "women"],
  ["mouse", "mice"],
  ["goose", "geese"],
  ["tooth", "teeth"],
  ["foot", "feet"],
  ["ox", "oxen"],
  ["criterion", "criteria"],
  ["datum", "data"],
  ["index", "indices"],
  ["matrix", "matrices"],
  ["vertex", "vertices"],
  ["analysis", "analyses"],
  ["basis", "bases"],
  ["crisis", "crises"],
  ["thesis", "theses"],
]);

export function pluralize(word: string): string {
  const lower = word.toLowerCase();

  if (UNCOUNTABLE.has(lower)) return word;

  const irregular = IRREGULAR.get(lower);
  if (irregular) {
    if (word[0] === word[0].toUpperCase()) {
      return irregular[0].toUpperCase() + irregular.slice(1);
    }
    return irregular;
  }

  if (/(s|x|z|ch|sh)$/i.test(word)) return word + "es";
  if (/[^aeiou]y$/i.test(word)) return word.slice(0, -1) + "ies";
  if (/f$/i.test(word)) return word.slice(0, -1) + "ves";
  if (/fe$/i.test(word)) return word.slice(0, -2) + "ves";

  return word + "s";
}

export function splitCamelCase(name: string): string[] {
  return name
    .replace(/([a-z0-9])([A-Z])/g, "$1\0$2")
    .replace(/([A-Z]+)([A-Z][a-z])/g, "$1\0$2")
    .split("\0");
}

export function toKebabCase(name: string): string {
  return splitCamelCase(name)
    .map((w) => w.toLowerCase())
    .join("-");
}

export function toSnakeCase(name: string): string {
  return splitCamelCase(name)
    .map((w) => w.toLowerCase())
    .join("_");
}

export function toPathSegment(entityName: string): string {
  const parts = splitCamelCase(entityName);
  const last = parts[parts.length - 1];
  parts[parts.length - 1] = pluralize(last);
  return parts.map((w) => w.toLowerCase()).join("-");
}

export function toTableName(entityName: string): string {
  const parts = splitCamelCase(entityName);
  const last = parts[parts.length - 1];
  parts[parts.length - 1] = pluralize(last);
  return parts.map((w) => w.toLowerCase()).join("_");
}

export function toColumnName(fieldName: string): string {
  if (fieldName.includes("_") || fieldName === fieldName.toLowerCase()) {
    return fieldName;
  }
  return toSnakeCase(fieldName);
}
