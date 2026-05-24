from fastapi import FastAPI


def register(app: FastAPI) -> None:
    '''Register custom routes, middleware, and lifecycle hooks.

    This file is never overwritten by `spec-to-rest compile`. Add user-defined
    endpoints, middleware, or startup/shutdown hooks here; the generated
    `app/main.py` calls this once after mounting all spec-derived routers.

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
