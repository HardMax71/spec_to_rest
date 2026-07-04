"""Initial schema for UrlShortener.

Revision ID: 001
Create Date: 2026-07-04
"""
from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa


revision: str = "001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "url_mappings",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("code", sa.String(length=255), nullable=False),
        sa.Column("url", sa.String(length=255), nullable=False),
        sa.Column("created_at", sa.DateTime(), nullable=False),
        sa.Column("click_count", sa.Integer(), nullable=False),
        sa.Column("updated_at", sa.DateTime(), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('char_length(code) >= 6', name="ck_url_mappings_0"),
        sa.CheckConstraint("code REGEXP '^[a-zA-Z0-9]+$'", name="ck_url_mappings_1"),
        sa.CheckConstraint('char_length(url) > 0', name="ck_url_mappings_2"),
        sa.CheckConstraint('click_count >= 0', name="ck_url_mappings_3"),
    )



def downgrade() -> None:
    op.drop_table("url_mappings")
