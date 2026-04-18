from sqlalchemy import delete as sa_delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.url_mapping import UrlMapping
from app.schemas.url_mapping import (
    ShortenRequest,
    UrlMappingRead,
)


class UrlMappingService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def shorten(self, body: ShortenRequest) -> None:
        raise NotImplementedError(
            "create operation 'Shorten' — implement in M4+"
        )

    async def list_all(self) -> list[UrlMappingRead]:
        result = await self._session.execute(select(UrlMapping))
        rows = result.scalars().all()
        return [UrlMappingRead.model_validate(row) for row in rows]

    async def resolve(self, code: str) -> str:
        raise NotImplementedError(
            "partial_update operation 'Resolve' — implement in M4+"
        )

    async def delete(self, code: str) -> bool:
        result = await self._session.execute(
            sa_delete(UrlMapping).where(UrlMapping.code == code)
        )
        return result.rowcount > 0
