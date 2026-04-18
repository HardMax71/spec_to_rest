from datetime import datetime
from pydantic import BaseModel, ConfigDict


class BaseCreate(BaseModel):
    created_at: datetime


class BaseRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    created_at: datetime


class BaseUpdate(BaseModel):
    created_at: datetime | None = None
