from sqlalchemy.ext.asyncio import AsyncSession

from app.models.inventory_entry import InventoryEntry


class InventoryEntryService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

