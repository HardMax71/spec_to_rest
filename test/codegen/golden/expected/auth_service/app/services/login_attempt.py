from sqlalchemy.ext.asyncio import AsyncSession

from app.models.login_attempt import LoginAttempt


class LoginAttemptService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

