from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.session import (
    SessionCreate,
    SessionRead,
)
from app.services.session import SessionService

router = APIRouter(tags=["session"])


@router.post("/auth/refresh", status_code=201)
async def refresh_token(
    body: SessionCreate,
    session: AsyncSession = Depends(get_session),
) -> SessionRead:
    svc = SessionService(session)
    return await svc.refresh_token(body)
