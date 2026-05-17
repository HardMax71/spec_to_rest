import express from 'express';
import type { Request, Response } from 'express';

import { errorHandler } from './middleware/error.js';
import { mountRoutes } from './routes/index.js';

// Prisma returns `bigint` for 64-bit auto-increment ids, which `JSON.stringify` cannot
// serialize. Emit it as a JSON number so the wire stays the spec's integer contract.
(BigInt.prototype as unknown as { toJSON: () => number }).toJSON = function (): number {
  return Number(this);
};

export const app = express();

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: false }));

app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({ status: 'ok' });
});

mountRoutes(app);

app.use(errorHandler);
