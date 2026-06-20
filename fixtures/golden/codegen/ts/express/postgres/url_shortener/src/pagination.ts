export const PAGE_LIMIT_DEFAULT = 50;
export const PAGE_LIMIT_MIN = 1;
export const PAGE_LIMIT_MAX = 100;
export const PAGE_OFFSET_DEFAULT = 0;

export interface Pagination {
  limit: number;
  offset: number;
}

const clampInt = (raw: unknown, fallback: number, min: number, max: number): number => {
  const parsed = Number.parseInt(String(raw ?? ''), 10);
  const value = Number.isNaN(parsed) ? fallback : parsed;
  return Math.min(max, Math.max(min, value));
};

export const paginationFromQuery = (query: {
  limit?: unknown;
  offset?: unknown;
}): Pagination => ({
  limit: clampInt(query.limit, PAGE_LIMIT_DEFAULT, PAGE_LIMIT_MIN, PAGE_LIMIT_MAX),
  offset: clampInt(query.offset, PAGE_OFFSET_DEFAULT, 0, Number.MAX_SAFE_INTEGER),
});
