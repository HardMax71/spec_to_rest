package specrest.codegen

object ExtensionStub:

  val python: String =
    """from fastapi import FastAPI
      |
      |
      |def register(app: FastAPI) -> None:
      |    '''Register custom routes, middleware, and lifecycle hooks.
      |
      |    This file is never overwritten by `spec-to-rest compile`. Add user-defined
      |    endpoints, middleware, or startup/shutdown hooks here; the generated
      |    `app/main.py` calls this once after mounting all spec-derived routers.
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
      |// Register lets you mount custom routes and middleware on top of the
      |// spec-derived ones. This file is never overwritten by `spec-to-rest
      |// compile`; the generated cmd/server/main.go calls Register once after
      |// mounting all spec-derived handlers.
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
      |// Register custom routes and middleware on top of the spec-derived ones.
      |// This file is never overwritten by `spec-to-rest compile`; the generated
      |// src/app.ts calls registerExtensions once after mounting all spec-derived
      |// routes.
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
