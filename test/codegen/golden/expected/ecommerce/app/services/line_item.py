from sqlalchemy.ext.asyncio import AsyncSession

from app.models.line_item import LineItem


class LineItemService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

