from pydantic import BaseModel, ConfigDict


class ChildCreate(BaseModel):
    name: str
    score: float


class ChildRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    score: float


class ChildUpdate(BaseModel):
    name: str | None = None
    score: float | None = None
