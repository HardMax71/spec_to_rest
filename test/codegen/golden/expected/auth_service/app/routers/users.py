from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.user import (
    UserCreate,
    UserRead,
    UserUpdate,
)
from app.services.user import UserService

router = APIRouter(tags=["user"])


@router.post("/auth/register", status_code=201)
async def register(
    body: UserCreate,
    session: AsyncSession = Depends(get_session),
) -> UserRead:
    svc = UserService(session)
    return await svc.register(body)

@router.post("/auth/login", status_code=200)
async def login(
    body: UserCreate,
    session: AsyncSession = Depends(get_session),
) -> UserRead:
    svc = UserService(session)
    return await svc.login(body)

@router.post("/users", status_code=201)
async def login_failed(
    body: UserCreate,
    session: AsyncSession = Depends(get_session),
) -> UserRead:
    svc = UserService(session)
    return await svc.login_failed(body)

@router.post("/auth/password-reset", status_code=200)
async def request_password_reset(
    body: UserUpdate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = UserService(session)
    return await svc.request_password_reset(body)

@router.post("/auth/password-reset/confirm", status_code=200)
async def reset_password(
    body: UserUpdate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = UserService(session)
    return await svc.reset_password(body)

@router.post("/auth/logout", status_code=204)
async def logout(
    body: UserUpdate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = UserService(session)
    return await svc.logout(body)
