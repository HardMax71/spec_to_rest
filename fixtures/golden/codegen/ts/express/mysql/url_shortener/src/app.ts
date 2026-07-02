import express from 'express';
import type { NextFunction, Request, Response } from 'express';
import { Counter, Histogram, collectDefaultMetrics, register } from 'prom-client';

import { registerExtensions } from './extensions/index.js';
import { errorHandler } from './middleware/error.js';
import { prisma } from './prisma.js';
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

collectDefaultMetrics();

const httpRequests = new Counter({
  name: 'http_requests_total',
  help: 'HTTP requests served, by method, route pattern, and status code.',
  labelNames: ['method', 'path', 'status'],
});
const httpRequestDuration = new Histogram({
  name: 'http_request_duration_seconds',
  help: 'HTTP request duration in seconds, by method and route pattern.',
  labelNames: ['method', 'path'],
});

export const app = express();

app.use(express.json({ limit: '1mb' }));
app.use(express.urlencoded({ extended: false }));

app.use((req: Request, res: Response, next: NextFunction) => {
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    // The matched route pattern, not the raw URL: raw paths embed ids and
    // would blow up label cardinality.
    const path = req.route ? `${req.baseUrl}${String(req.route.path)}` : 'unmatched';
    const seconds = Number(process.hrtime.bigint() - start) / 1e9;
    httpRequests.labels(req.method, path, String(res.statusCode)).inc();
    httpRequestDuration.labels(req.method, path).observe(seconds);
  });
  next();
});

app.get('/health', (_req: Request, res: Response) => {
  res.status(200).json({ status: 'ok' });
});

app.get('/ready', async (_req: Request, res: Response) => {
  try {
    await prisma.$queryRaw`SELECT 1`;
    res.status(200).json({ status: 'ready' });
  } catch {
    res.status(503).json({ status: 'unavailable' });
  }
});

app.get('/metrics', async (_req: Request, res: Response, next: NextFunction) => {
  try {
    res.set('Content-Type', register.contentType);
    res.send(await register.metrics());
  } catch (err) {
    next(err);
  }
});

// Express applies middleware only to routes registered after the
// `app.use(...)` call, so the user hook runs before generated routes are
// mounted — middleware installed inside `registerExtensions` wraps every
// spec-derived endpoint, and any extra routes added here take precedence
// on collision.
registerExtensions(app);
mountRoutes(app);

app.use(errorHandler);
