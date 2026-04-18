from datetime import datetime
from pydantic import BaseModel, ConfigDict


class UserCreate(BaseModel):
    email: str
    password_hash: str
    display_name: str
    created_at: datetime
    last_login: datetime | None
    is_active: bool


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    password_hash: str
    display_name: str
    created_at: datetime
    last_login: datetime | None
    is_active: bool


class UserUpdate(BaseModel):
    email: str | None = None
    password_hash: str | None = None
    display_name: str | None = None
    created_at: datetime | None = None
    last_login: datetime | None = None
    is_active: bool | None = None
