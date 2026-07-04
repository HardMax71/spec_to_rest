import type { Express, Request, Response } from 'express';

import { requireAdmin } from '../middleware/auth.js';
import { prisma } from '../prisma.js';

// Bearer-guarded admin surface (state export / data import / re-initialize). The
// conformance suite is its primary client; the contract (reset / state / seed) is
// identical across the fastapi / chi / express targets.

type AnyPrisma = Record<string, {
  deleteMany: () => Promise<unknown>;
  findMany: (args?: unknown) => Promise<Array<Record<string, unknown>>>;
  findFirst: () => Promise<Record<string, unknown> | null>;
  create: (args: { data: Record<string, unknown> }) => Promise<Record<string, unknown>>;
  updateMany: (args: { data: Record<string, unknown> }) => Promise<unknown>;
}>;

function rowToDict_UrlMapping(r: Record<string, unknown>): Record<string, unknown> {
  return {
    code: r.code,
    url: r.url,
    created_at: r.createdAt == null ? null : new Date(r.createdAt as string | Date).toISOString(),
    click_count: r.clickCount,
  };
}

export const registerAdminRoutes = (app: Express): void => {
  app.post('/admin/reset', requireAdmin, (_req: Request, res: Response): void => {
    void (async () => {
      await (prisma as unknown as AnyPrisma).urlMapping.deleteMany();
      res.status(204).end();
    })().catch((e: unknown) => {
      res.status(500).json({ detail: String(e) });
    });
  });

  app.get('/admin/state', requireAdmin, (_req: Request, res: Response): void => {
    void (async () => {
      const rows_UrlMapping = await (prisma as unknown as AnyPrisma).urlMapping.findMany();
      res.status(200).json({
        store: Object.fromEntries(
          rows_UrlMapping.map((r: Record<string, unknown>) => [
            String(r.code), r.url,
          ]),
        ),
        metadata: Object.fromEntries(
          rows_UrlMapping.map((r: Record<string, unknown>) => [
            String(r.code), rowToDict_UrlMapping(r),
          ]),
        ),
        base_url: null,
      });
    })().catch((e: unknown) => {
      res.status(500).json({ detail: String(e) });
    });
  });
};
