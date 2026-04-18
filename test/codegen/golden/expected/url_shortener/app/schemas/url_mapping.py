from datetime import datetime
from pydantic import BaseModel, ConfigDict


class UrlMappingCreate(BaseModel):
    code: str
    url: str
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
    code: str | None = None
    url: str | None = None
    created_at: datetime | None = None
    click_count: int | None = None
