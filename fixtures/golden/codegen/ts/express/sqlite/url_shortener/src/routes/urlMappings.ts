import type { Express, NextFunction, Request, Response } from 'express';

import { NotFound } from '../middleware/error.js';
import { validateBody } from '../middleware/validate.js';
import * as service from '../services/urlMapping.js';
import {
  UrlMappingCreateSchema,
  ShortenRequestSchema,
} from '../schemas/urlMapping.js';

const wrap =
  (handler: (req: Request, res: Response) => Promise<void>) =>
  (req: Request, res: Response, next: NextFunction): void => {
    handler(req, res).catch(next);
  };

export const registerUrlMappingRoutes = (app: Express): void => {
  app.post(
    '/shorten',
    validateBody(ShortenRequestSchema),
    wrap(async (req, res) => {
      const body = req.body as service.ShortenRequest;
      await service.shorten(body);
      res.status(201).end();
    }),
  );

  app.get(
    '/urls',
    wrap(async (_req, res) => {
      const result = await service.listAll();
      res.status(200).json(result);
    }),
  );

  app.get(
    '/:code',
    wrap(async (req, res) => {
      const code = req.params['code'] ?? '';
      const result = await service.resolve(code);
      if (result === null) {
        throw NotFound();
      }
      res.redirect(302, result.url);
    }),
  );

  app.delete(
    '/:code',
    wrap(async (req, res) => {
      const code = req.params['code'] ?? '';
      const ok = await service.delete_(code);
      if (!ok) {
        throw NotFound();
      }
      res.status(204).end();
    }),
  );

};
