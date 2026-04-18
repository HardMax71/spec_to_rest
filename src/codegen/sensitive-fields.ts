const EXACT: ReadonlySet<string> = new Set([
  "password",
  "password_hash",
  "secret",
  "token",
  "api_key",
]);

const SUFFIXES: readonly string[] = [
  "_hash",
  "_secret",
  "_password",
  "_api_key",
  "_token",
];

export function isSensitiveFieldName(name: string): boolean {
  if (EXACT.has(name)) return true;
  return SUFFIXES.some((s) => name.endsWith(s));
}
