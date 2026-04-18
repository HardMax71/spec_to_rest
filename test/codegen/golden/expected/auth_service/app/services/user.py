from sqlalchemy.ext.asyncio import AsyncSession

from app.models.user import User
from app.schemas.user import (
    UserCreate,
    UserRead,
    UserUpdate,
)


class UserService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def register(self, body: UserCreate) -> UserRead:
        row = User(**body.model_dump())
        self._session.add(row)
        await self._session.flush()
        return UserRead.model_validate(row)

    async def login(self, body: UserCreate) -> UserRead:
        row = User(**body.model_dump())
        self._session.add(row)
        await self._session.flush()
        return UserRead.model_validate(row)

    async def login_failed(self, body: UserCreate) -> UserRead:
        row = User(**body.model_dump())
        self._session.add(row)
        await self._session.flush()
        return UserRead.model_validate(row)

    async def request_password_reset(self, body: UserUpdate) -> None:
        raise NotImplementedError(
            "partial_update operation 'RequestPasswordReset' — implement in M4+"
        )

    async def reset_password(self, body: UserUpdate) -> None:
        raise NotImplementedError(
            "partial_update operation 'ResetPassword' — implement in M4+"
        )

    async def logout(self, body: UserUpdate) -> None:
        raise NotImplementedError(
            "partial_update operation 'Logout' — implement in M4+"
        )
