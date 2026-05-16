from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import engine
from app.redaction import configure_logging

from app.routers import url_mappings



configure_logging()


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    yield
    await engine.dispose()


app = FastAPI(title="UrlShortener", version="0.1.0", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=[
        "http://localhost:3000",
        "http://localhost:5173",
        "http://127.0.0.1:3000",
        "http://127.0.0.1:5173",
    ],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


app.include_router(url_mappings.router)



import os as _os
if _os.environ.get("ENABLE_TEST_ADMIN") == "1":
    try:
        from app.routers import test_admin as _test_admin
        app.include_router(_test_admin.router)
    except ImportError:
        pass


@app.get("/health", tags=["infrastructure"])
async def health_check() -> dict[str, str]:
    return {"status": "ok"}
