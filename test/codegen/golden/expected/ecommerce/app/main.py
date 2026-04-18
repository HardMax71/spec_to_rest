from contextlib import asynccontextmanager
from collections.abc import AsyncIterator

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.database import engine
from app.routers import products
from app.routers import line_items
from app.routers import orders
from app.routers import payments
from app.routers import inventory_entries


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    yield
    await engine.dispose()


app = FastAPI(title="OrderService", version="0.1.0", lifespan=lifespan)

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

app.include_router(products.router)
app.include_router(line_items.router)
app.include_router(orders.router)
app.include_router(payments.router)
app.include_router(inventory_entries.router)


@app.get("/health", tags=["infrastructure"])
async def health_check() -> dict[str, str]:
    return {"status": "ok"}
