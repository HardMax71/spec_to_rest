from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.services.login_attempt import LoginAttemptService

router = APIRouter(tags=["login_attempt"])

