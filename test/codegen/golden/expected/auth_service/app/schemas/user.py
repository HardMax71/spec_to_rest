from datetime import datetime
from pydantic import BaseModel, ConfigDict


class UserCreate(BaseModel):
    email: str
    password_hash: str
    display_name: str
    created_at: datetime
    last_login: datetime | None = None
    is_active: bool


class UserRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    email: str
    display_name: str
    created_at: datetime
    last_login: datetime | None = None
    is_active: bool


class UserUpdate(BaseModel):
    email: str | None = None
    password_hash: str | None = None
    display_name: str | None = None
    created_at: datetime | None = None
    last_login: datetime | None = None
    is_active: bool | None = None


class RegisterRequest(BaseModel):
    email: str
    password: str
    display_name: str


class LoginRequest(BaseModel):
    email: str
    password: str


class LoginFailedRequest(BaseModel):
    email: str
    password: str


class RequestPasswordResetRequest(BaseModel):
    email: str


class ResetPasswordRequest(BaseModel):
    reset_token: str
    new_password: str


class LogoutRequest(BaseModel):
    access_token: str
