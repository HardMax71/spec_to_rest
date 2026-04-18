from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.services.payment import PaymentService

router = APIRouter(tags=["payment"])

