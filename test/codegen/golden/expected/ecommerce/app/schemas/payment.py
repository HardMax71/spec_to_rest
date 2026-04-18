from datetime import datetime
from pydantic import BaseModel, ConfigDict


class PaymentCreate(BaseModel):
    order_id: int
    amount: int
    status: str
    created_at: datetime


class PaymentRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    order_id: int
    amount: int
    status: str
    created_at: datetime


class PaymentUpdate(BaseModel):
    order_id: int | None = None
    amount: int | None = None
    status: str | None = None
    created_at: datetime | None = None
