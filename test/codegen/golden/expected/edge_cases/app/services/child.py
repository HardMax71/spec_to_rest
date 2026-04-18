from sqlalchemy.ext.asyncio import AsyncSession

from app.models.child import Child


class ChildService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

