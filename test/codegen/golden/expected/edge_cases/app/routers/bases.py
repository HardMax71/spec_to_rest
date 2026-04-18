from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.base import (
    BaseRead,
)
from app.services.base import BaseService

router = APIRouter(tags=["base"])


@router.get("/bases", status_code=200)
async def with_constructor(
    session: AsyncSession = Depends(get_session),
) -> list[BaseRead]:
    svc = BaseService(session)
    return await svc.with_constructor()
