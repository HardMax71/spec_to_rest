from sqlalchemy.ext.asyncio import AsyncSession

from app.schemas.user import (
    LoginFailedRequest,
    LoginRequest,
    LogoutRequest,
    RegisterRequest,
    RequestPasswordResetRequest,
    ResetPasswordRequest,
)


class UserService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def register(self, body: RegisterRequest) -> None:
        raise NotImplementedError(
            "create operation 'Register' — implement in M4+"
        )

    async def login(self, body: LoginRequest) -> None:
        raise NotImplementedError(
            "create operation 'Login' — implement in M4+"
        )

    async def login_failed(self, body: LoginFailedRequest) -> None:
        raise NotImplementedError(
            "create operation 'LoginFailed' — implement in M4+"
        )

    async def request_password_reset(self, body: RequestPasswordResetRequest) -> None:
        raise NotImplementedError(
            "partial_update operation 'RequestPasswordReset' — implement in M4+"
        )

    async def reset_password(self, body: ResetPasswordRequest) -> None:
        raise NotImplementedError(
            "partial_update operation 'ResetPassword' — implement in M4+"
        )

    async def logout(self, body: LogoutRequest) -> None:
        raise NotImplementedError(
            "partial_update operation 'Logout' — implement in M4+"
        )
