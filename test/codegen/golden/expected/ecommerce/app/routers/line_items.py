from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.services.line_item import LineItemService

router = APIRouter(tags=["line_item"])

