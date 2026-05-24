import type { Express } from 'express';

// Register custom routes and middleware on top of the spec-derived ones.
// This file is never overwritten by `spec-to-rest compile`; the generated
// src/app.ts calls registerExtensions once after mounting all spec-derived
// routes.
//
// Example:
//
//   export function registerExtensions(app: Express): void {
//     app.get('/custom/ping', (_req, res) => res.status(200).json({ status: 'ok' }));
//   }
export function registerExtensions(app: Express): void {
  void app;
}
