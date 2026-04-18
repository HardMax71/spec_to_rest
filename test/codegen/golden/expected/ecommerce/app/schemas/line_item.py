from pydantic import BaseModel, ConfigDict


class LineItemCreate(BaseModel):
    product_sku: str
    quantity: int
    unit_price: int
    line_total: int


class LineItemRead(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    product_sku: str
    quantity: int
    unit_price: int
    line_total: int


class LineItemUpdate(BaseModel):
    product_sku: str | None = None
    quantity: int | None = None
    unit_price: int | None = None
    line_total: int | None = None
