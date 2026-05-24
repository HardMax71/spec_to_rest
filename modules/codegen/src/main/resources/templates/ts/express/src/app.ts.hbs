import express from 'express';
import type { Request, Response } from 'express';

import { registerExtensions } from './extensions/index.js';
import { errorHandler } from './middleware/error.js';
import { mountRoutes } from './routes/index.js';

// Prisma returns `bigint` for 64-bit auto-increment ids, which `JSON.stringify` cannot
// serialize. Emit it as a JSON number so the wire stays the spec's integer contract
// (matching the fastapi/go targets). Fail loud rather than silently round an id past the
// JS safe-integer range — realistic auto-increment ids never approach 2^53.
(BigInt.prototype as unknown as { toJSON: () => number }).toJSON = function (): number {
  const n = Number(this);
  if (!Number.isSafeInteger(n)) {
    throw new Error(
      `cannot serialize id ${this.toString()}: exceeds JS safe-integer range`,
    );
  }
  return n;
};

export const app = express();

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: false }));

app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({ status: 'ok' });
});

// Express applies middleware only to routes registered after the
// `app.use(...)` call, so the user hook runs before generated routes are
// mounted — middleware installed inside `registerExtensions` wraps every
// spec-derived endpoint, and any extra routes added here take precedence
// on collision.
registerExtensions(app);
mountRoutes(app);

app.use(errorHandler);
