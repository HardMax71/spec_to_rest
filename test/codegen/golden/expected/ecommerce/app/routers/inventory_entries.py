from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.services.inventory_entry import InventoryEntryService

router = APIRouter(tags=["inventory_entry"])

