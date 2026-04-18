from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.base import Base
from app.schemas.base import (
    BaseRead,
)


class BaseService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def with_constructor(self) -> list[BaseRead]:
        result = await self._session.execute(select(Base))
        rows = result.scalars().all()
        return [BaseRead.model_validate(row) for row in rows]
