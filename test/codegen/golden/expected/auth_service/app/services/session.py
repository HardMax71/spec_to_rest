from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.session import (
    RefreshTokenRequest,
)


class SessionService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def refresh_token(self, body: RefreshTokenRequest) -> None:
        raise NotImplementedError(
            "create operation 'RefreshToken' — implement in M4+"
        )
