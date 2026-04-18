from datetime import datetime
from pydantic import BaseModel, ConfigDict


class SessionCreate(BaseModel):
    user_id: int
    access_token: str
    refresh_token: str
    access_expires_at: datetime
    refresh_expires_at: datetime
    created_at: datetime
    is_revoked: bool


class SessionRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    user_id: int
    access_token: str
    refresh_token: str
    access_expires_at: datetime
    refresh_expires_at: datetime
    created_at: datetime
    is_revoked: bool


class SessionUpdate(BaseModel):
    user_id: int | None = None
    access_token: str | None = None
    refresh_token: str | None = None
    access_expires_at: datetime | None = None
    refresh_expires_at: datetime | None = None
    created_at: datetime | None = None
    is_revoked: bool | None = None
