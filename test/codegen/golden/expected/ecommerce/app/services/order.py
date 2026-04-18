from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.order import Order
from app.schemas.order import (
    AddLineItemRequest,
    CreateDraftOrderRequest,
    OrderRead,
    RecordPaymentRequest,
)


class OrderService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create_draft_order(self, body: CreateDraftOrderRequest) -> None:
        raise NotImplementedError(
            "create operation 'CreateDraftOrder' — implement in M4+"
        )

    async def list_orders(self) -> list[OrderRead]:
        result = await self._session.execute(select(Order))
        rows = result.scalars().all()
        return [OrderRead.model_validate(row) for row in rows]

    async def add_line_item(self, order_id: int, body: AddLineItemRequest) -> None:
        raise NotImplementedError(
            "partial_update operation 'AddLineItem' — implement in M4+"
        )

    async def place_order(self, order_id: int) -> None:
        raise NotImplementedError(
            "transition operation 'PlaceOrder' — implement in M4+"
        )

    async def record_payment(self, order_id: int, body: RecordPaymentRequest) -> None:
        raise NotImplementedError(
            "transition operation 'RecordPayment' — implement in M4+"
        )

    async def ship_order(self, order_id: int) -> None:
        raise NotImplementedError(
            "transition operation 'ShipOrder' — implement in M4+"
        )

    async def confirm_delivery(self, order_id: int) -> None:
        raise NotImplementedError(
            "transition operation 'ConfirmDelivery' — implement in M4+"
        )

    async def cancel_order(self, order_id: int) -> None:
        raise NotImplementedError(
            "transition operation 'CancelOrder' — implement in M4+"
        )

    async def process_return(self, order_id: int) -> None:
        raise NotImplementedError(
            "transition operation 'ProcessReturn' — implement in M4+"
        )

    async def get_order(self, order_id: int) -> OrderRead | None:
        result = await self._session.execute(
            select(Order).where(Order.id == order_id)
        )
        row = result.scalar_one_or_none()
        return OrderRead.model_validate(row) if row is not None else None

    async def remove_line_item(self, order_id: int, item_id: int) -> None:
        raise NotImplementedError(
            "partial_update operation 'RemoveLineItem' — implement in M4+"
        )
