from pydantic import BaseModel, ConfigDict


class ProductCreate(BaseModel):
    sku: str
    name: str
    price: int
    description: str | None = None


class ProductRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    sku: str
    name: str
    price: int
    description: str | None = None


class ProductUpdate(BaseModel):
    sku: str | None = None
    name: str | None = None
    price: int | None = None
    description: str | None = None
