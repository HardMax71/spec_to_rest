from sqlalchemy.ext.asyncio import AsyncSession

from app.models.payment import Payment


class PaymentService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

