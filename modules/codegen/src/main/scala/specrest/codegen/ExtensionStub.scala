package specrest.codegen

object ExtensionStub:

  val python: String =
    """from fastapi import FastAPI
      |
      |
      |def register(app: FastAPI) -> None:
      |    '''Register custom routes, middleware, and lifecycle hooks.
      |
      |    This file is never overwritten by `spec-to-rest compile`. The generated
      |    `app/main.py` calls this once BEFORE mounting any spec-derived router,
      |    so middleware added here wraps every generated endpoint and routes
      |    declared here take precedence on path collisions.
      |
      |    Example:
      |
      |        from fastapi import APIRouter
      |
      |        custom = APIRouter(prefix="/custom", tags=["custom"])
      |
      |        @custom.get("/ping")
      |        async def ping() -> dict[str, str]:
      |            return {"status": "ok"}
      |
      |        def register(app: FastAPI) -> None:
      |            app.include_router(custom)
      |    '''
      |    del app
      |""".stripMargin

  val go: String =
    """package extensions
      |
      |import (
      |	"github.com/go-chi/chi/v5"
      |	"github.com/uptrace/bun"
      |)
      |
      |// Register installs custom routes and middleware. This file is never
      |// overwritten by `spec-to-rest compile`.
      |//
      |// The generated cmd/server/main.go calls Register BEFORE wiring any
      |// spec-derived route — that ordering is mandatory because chi panics on
      |// r.Use(...) after a route has been registered ("chi: all middlewares
      |// must be defined before routes on a mux"). Middleware installed here
      |// therefore wraps every generated handler, and routes added here take
      |// precedence on path collisions (chi panics on duplicate registrations,
      |// so deliberately shadowing a generated path is not supported).
      |//
      |// Example:
      |//
      |//	func Register(r chi.Router, db *bun.DB) {
      |//		r.Get("/custom/ping", func(w http.ResponseWriter, _ *http.Request) {
      |//			_, _ = w.Write([]byte(`{"status":"ok"}`))
      |//		})
      |//	}
      |func Register(r chi.Router, db *bun.DB) {
      |	_ = r
      |	_ = db
      |}
      |""".stripMargin

  val ts: String =
    """import type { Express } from 'express';
      |
      |// registerExtensions installs custom routes and middleware. This file is
      |// never overwritten by `spec-to-rest compile`.
      |//
      |// The generated src/app.ts calls registerExtensions BEFORE mounting any
      |// spec-derived route, because Express only applies `app.use(...)` to
      |// routes registered after the call. Middleware installed here therefore
      |// wraps every generated endpoint, and any extra routes declared here
      |// take precedence on path collisions.
      |//
      |// Example:
      |//
      |//   export function registerExtensions(app: Express): void {
      |//     app.get('/custom/ping', (_req, res) => res.status(200).json({ status: 'ok' }));
      |//   }
      |export function registerExtensions(app: Express): void {
      |  void app;
      |}
      |""".stripMargin
