
from datetime import datetime


from sqlalchemy import DateTime, Integer, String


from sqlalchemy.orm import Mapped, mapped_column

from app.db.base import Base
from app.schemas.url_mapping import UrlMappingCreate


class UrlMapping(Base):
    __tablename__ = "url_mappings"

    id: Mapped[int] = mapped_column(primary_key=True)

    code: Mapped[str] = mapped_column(String)

    url: Mapped[str] = mapped_column(String)

    created_at: Mapped[datetime] = mapped_column(DateTime)

    click_count: Mapped[int] = mapped_column(Integer)


    def __init__(self, body: UrlMappingCreate) -> None:
        super().__init__(

            code=body.code,

            url=body.url,

            created_at=body.created_at,

            click_count=body.click_count,

        )
