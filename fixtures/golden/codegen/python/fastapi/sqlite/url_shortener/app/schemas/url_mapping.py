from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class UrlMappingCreate(BaseModel):
    code: str = Field(min_length=6, pattern=r"^(?:^[a-zA-Z0-9]+$)$")
    url: str = Field(min_length=1, pattern=r"^(?:^https?://[^\s]+)$")
    created_at: datetime
    click_count: int


class UrlMappingRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    code: str
    url: str
    created_at: datetime
    click_count: int


class UrlMappingUpdate(BaseModel):
    code: str | None = Field(default=None, min_length=6, pattern=r"^(?:^[a-zA-Z0-9]+$)$")
    url: str | None = Field(default=None, min_length=1, pattern=r"^(?:^https?://[^\s]+)$")
    created_at: datetime | None = None
    click_count: int | None = None


class ShortenRequest(BaseModel):
    url: str = Field(min_length=1, pattern=r"^(?:^https?://[^\s]+)$")
