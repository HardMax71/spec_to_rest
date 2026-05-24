import type { Express } from 'express';

// registerExtensions installs custom routes and middleware. This file is
// never overwritten by `spec-to-rest compile`.
//
// The generated src/app.ts calls registerExtensions BEFORE mounting any
// spec-derived route, because Express only applies `app.use(...)` to
// routes registered after the call. Middleware installed here therefore
// wraps every generated endpoint, and any extra routes declared here
// take precedence on path collisions.
//
// Example:
//
//   export function registerExtensions(app: Express): void {
//     app.get('/custom/ping', (_req, res) => res.status(200).json({ status: 'ok' }));
//   }
export function registerExtensions(app: Express): void {
  void app;
}
