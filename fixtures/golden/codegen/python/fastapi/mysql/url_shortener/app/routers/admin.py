from datetime import date, datetime
from typing import Any

from fastapi import APIRouter, Depends
from fastapi.responses import Response
from sqlalchemy import delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.models.url_mapping import UrlMapping
from app.security import require_admin

router = APIRouter(prefix="/admin", tags=["admin"], dependencies=[Depends(require_admin)])


def _row_to_dict(row: Any) -> dict[str, Any]:
    out: dict[str, Any] = {}
    for col in row.__table__.columns:
        v = getattr(row, col.name)
        if isinstance(v, (datetime, date)):
            # Same canonical wire form as the API's responses; drivers
            # hand back naive datetimes for UTC-stored columns.
            iso = v.isoformat()
            v = iso.replace("+00:00", "Z") if iso.endswith("+00:00") else f"{iso}Z"
        out[col.name] = v
    return out


def _parse_iso(value: Any) -> Any:
    if isinstance(value, str):
        return datetime.fromisoformat(value)
    return value


@router.post("/reset", status_code=204)
async def reset(session: AsyncSession = Depends(get_session)) -> Response:
    await session.execute(delete(UrlMapping))
    # Committing in dependency teardown can land after the response,
    # racing a client's next request against the deletes.
    await session.commit()
    return Response(status_code=204)


@router.get("/state")
async def get_state(session: AsyncSession = Depends(get_session)) -> dict[str, Any]:
    rows = (await session.execute(select(UrlMapping))).scalars().all()
    return {
        "store": {row.code: row.url for row in rows},
        "metadata": {row.code: _row_to_dict(row) for row in rows},
        # M5.1: state field 'base_url' not backed by entity table
        "base_url": None,
    }
