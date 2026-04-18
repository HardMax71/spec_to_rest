from pydantic import BaseModel, ConfigDict


class InventoryEntryCreate(BaseModel):
    sku: str
    available: int
    reserved: int


class InventoryEntryRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    sku: str
    available: int
    reserved: int


class InventoryEntryUpdate(BaseModel):
    sku: str | None = None
    available: int | None = None
    reserved: int | None = None
