from datetime import datetime
from pydantic import BaseModel, ConfigDict


class OrderCreate(BaseModel):
    customer_id: int
    status: str
    items: list[int]
    subtotal: int
    tax: int
    total: int
    created_at: datetime
    updated_at: datetime
    shipped_at: datetime | None = None
    delivered_at: datetime | None = None


class OrderRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    customer_id: int
    status: str
    items: list[int]
    subtotal: int
    tax: int
    total: int
    created_at: datetime
    updated_at: datetime
    shipped_at: datetime | None = None
    delivered_at: datetime | None = None


class OrderUpdate(BaseModel):
    customer_id: int | None = None
    status: str | None = None
    items: list[int] | None = None
    subtotal: int | None = None
    tax: int | None = None
    total: int | None = None
    created_at: datetime | None = None
    updated_at: datetime | None = None
    shipped_at: datetime | None = None
    delivered_at: datetime | None = None


class CreateDraftOrderRequest(BaseModel):
    customer_id: int


class AddLineItemRequest(BaseModel):
    sku: str
    quantity: int


class RecordPaymentRequest(BaseModel):
    amount: int
