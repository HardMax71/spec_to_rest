import { timingSafeEqual } from 'node:crypto';

import type { NextFunction, Request, Response } from 'express';

import { config } from '../config.js';

export const requireAdmin = (req: Request, res: Response, next: NextFunction): void => {
  const token = config.adminToken;
  // No token configured (the production default): the surface does not exist.
  if (token === undefined || token === '') {
    res.status(404).json({ detail: 'Not Found' });
    return;
  }
  const header = req.get('authorization') ?? '';
  // Scheme match is case-insensitive (RFC 7235), like FastAPI's HTTPBearer.
  const presented = /^bearer /i.test(header) ? header.slice('bearer '.length) : '';
  const a = Buffer.from(presented);
  const b = Buffer.from(token);
  if (a.length !== b.length || !timingSafeEqual(a, b)) {
    res.set('WWW-Authenticate', 'Bearer').status(401).json({ detail: 'Unauthorized' });
    return;
  }
  next();
};
