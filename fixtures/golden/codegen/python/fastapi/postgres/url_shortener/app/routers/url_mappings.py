from fastapi import APIRouter, Depends, HTTPException, Response
from fastapi.responses import RedirectResponse
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.url_mapping import (
    ShortenRequest,
    UrlMappingRead,
)
from app.services.url_mapping import UrlMappingService

router = APIRouter(tags=["url_mapping"])


@router.post("/shorten", status_code=201)
async def shorten(
    body: ShortenRequest,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = UrlMappingService(session)
    return await svc.shorten(body)

@router.get("/urls", status_code=200)
async def list_all(
    session: AsyncSession = Depends(get_session),
) -> list[UrlMappingRead]:
    svc = UrlMappingService(session)
    return await svc.list_all()

@router.get("/{code}", status_code=302)
async def resolve(
    code: str,
    session: AsyncSession = Depends(get_session),
) -> RedirectResponse:
    svc = UrlMappingService(session)
    url = await svc.resolve(code)
    return RedirectResponse(url=url, status_code=302)

@router.delete("/{code}", status_code=204)
async def delete(
    code: str,
    session: AsyncSession = Depends(get_session),
) -> Response:
    svc = UrlMappingService(session)
    deleted = await svc.delete(code)
    if not deleted:
        raise HTTPException(status_code=404, detail="not found")
    return Response(status_code=204)
