from fastapi import FastAPI


def register(app: FastAPI) -> None:
    '''Register custom routes, middleware, and lifecycle hooks.

    This file is never overwritten by `spec-to-rest compile`. The generated
    `app/main.py` calls this once BEFORE mounting any spec-derived router,
    so middleware added here wraps every generated endpoint and routes
    declared here take precedence on path collisions.

    Example:

        from fastapi import APIRouter

        custom = APIRouter(prefix="/custom", tags=["custom"])

        @custom.get("/ping")
        async def ping() -> dict[str, str]:
            return {"status": "ok"}

        def register(app: FastAPI) -> None:
            app.include_router(custom)
    '''
    del app
