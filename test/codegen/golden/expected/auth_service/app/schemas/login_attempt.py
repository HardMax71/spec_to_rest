from datetime import datetime
from pydantic import BaseModel, ConfigDict


class LoginAttemptCreate(BaseModel):
    email: str
    timestamp: datetime
    success: bool


class LoginAttemptRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    timestamp: datetime
    success: bool


class LoginAttemptUpdate(BaseModel):
    email: str | None = None
    timestamp: datetime | None = None
    success: bool | None = None
