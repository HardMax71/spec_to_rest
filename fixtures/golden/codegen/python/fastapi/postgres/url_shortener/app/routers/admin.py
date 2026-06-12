from datetime import datetime, date

from fastapi import APIRouter, Body, Depends
from fastapi.responses import Response
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.security import require_admin
from app.models.url_mapping import UrlMapping

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])


def _row_to_dict(row) -> dict:
    out: dict = {}
    for col in row.__table__.columns:
        v = getattr(row, col.name)
        if isinstance(v, (datetime, date)):
            v = v.isoformat()
        out[col.name] = v
    return out


def _parse_iso(value):
    if isinstance(value, str):
        return datetime.fromisoformat(value)
    return value


@router.post("/reset", status_code=204)
async def reset(session: AsyncSession = Depends(get_session)) -> Response:
    await session.execute(delete(UrlMapping))
    return Response(status_code=204)


@router.get("/state")
async def get_state(session: AsyncSession = Depends(get_session)) -> dict:
    rows = (await session.execute(select(UrlMapping))).scalars().all()
    return {
        "store": {row.code: row.url for row in rows},
        "metadata": {row.code: _row_to_dict(row) for row in rows},
        # M5.1: state field 'base_url' not backed by entity table
        "base_url": None,
    }
