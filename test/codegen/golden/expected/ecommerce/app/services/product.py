from sqlalchemy.ext.asyncio import AsyncSession

from app.models.product import Product


class ProductService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

