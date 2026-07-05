import type { NextFunction, Request, Response } from 'express';
import { ZodError } from 'zod';

import { InvalidInputError } from '../dafnyKernel/adapter.js';

export class HttpError extends Error {
  constructor(
    public readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'HttpError';
  }
}

export const NotFound = (msg = 'not found'): HttpError => new HttpError(404, msg);

export const errorHandler = (
  err: unknown,
  _req: Request,
  res: Response,
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  _next: NextFunction,
): void => {
  if (err instanceof ZodError) {
    res.status(422).json({ detail: 'validation failed', errors: err.errors });
    return;
  }
  if (err instanceof InvalidInputError) {
    res.status(422).json({ detail: err.message });
    return;
  }
  if (err instanceof HttpError) {
    res.status(err.status).json({ detail: err.message });
    return;
  }
  const message = err instanceof Error ? err.message : 'internal error';
  res.status(500).json({ detail: message });
};
