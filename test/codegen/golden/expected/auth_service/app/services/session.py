from sqlalchemy.ext.asyncio import AsyncSession

from app.models.session import Session
from app.schemas.session import (
    SessionCreate,
    SessionRead,
)


class SessionService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def refresh_token(self, body: SessionCreate) -> SessionRead:
        row = Session(**body.model_dump())
        self._session.add(row)
        await self._session.flush()
        return SessionRead.model_validate(row)
