from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.order import (
    OrderCreate,
    OrderRead,
    OrderUpdate,
)
from app.services.order import OrderService

router = APIRouter(tags=["order"])


@router.post("/orders", status_code=201)
async def create_draft_order(
    body: OrderCreate,
    session: AsyncSession = Depends(get_session),
) -> OrderRead:
    svc = OrderService(session)
    return await svc.create_draft_order(body)

@router.get("/orders", status_code=200)
async def list_orders(
    session: AsyncSession = Depends(get_session),
) -> list[OrderRead]:
    svc = OrderService(session)
    return await svc.list_orders()

@router.post("/orders/{order_id}/items", status_code=201)
async def add_line_item(
    order_id: int,
    body: OrderUpdate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.add_line_item(order_id, body)

@router.post("/orders/{order_id}/place", status_code=200)
async def place_order(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.place_order(order_id)

@router.post("/orders/{order_id}/payments", status_code=201)
async def record_payment(
    order_id: int,
    body: OrderCreate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.record_payment(order_id, body)

@router.post("/orders/{order_id}/ship", status_code=200)
async def ship_order(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.ship_order(order_id)

@router.post("/orders/{order_id}/deliver", status_code=200)
async def confirm_delivery(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.confirm_delivery(order_id)

@router.post("/orders/{order_id}/cancel", status_code=200)
async def cancel_order(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.cancel_order(order_id)

@router.post("/orders/{order_id}/return", status_code=200)
async def process_return(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.process_return(order_id)

@router.get("/orders/{order_id}", status_code=200)
async def get_order(
    order_id: int,
    session: AsyncSession = Depends(get_session),
) -> OrderRead:
    svc = OrderService(session)
    result = await svc.get_order(order_id)
    if result is None:
        raise HTTPException(status_code=404, detail="not found")
    return result

@router.delete("/orders/{order_id}/items/{item_id}", status_code=204)
async def remove_line_item(
    order_id: int,
    item_id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = OrderService(session)
    return await svc.remove_line_item(order_id, item_id)
