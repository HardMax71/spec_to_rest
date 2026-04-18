"""Initial schema for TodoList.

Revision ID: 001
Create Date: 2026-04-18
"""
from collections.abc import Sequence

from alembic import op
import sqlalchemy as sa
from sqlalchemy.dialects import postgresql


revision: str = "001"
down_revision: str | None = None
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None


def upgrade() -> None:
    op.create_table(
        "todos",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("title", sa.Text(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("priority", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("completed_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("tags", postgresql.JSONB(), server_default=sa.text("'[]'::jsonb"), nullable=False),
        sa.CheckConstraint('id > 0', name="ck_todos_0"),
        sa.CheckConstraint('length(title) >= 1', name="ck_todos_1"),
        sa.CheckConstraint('length(title) <= 200', name="ck_todos_2"),
        sa.CheckConstraint("status IN ('TODO', 'IN_PROGRESS', 'DONE', 'ARCHIVED')", name="ck_todos_3"),
        sa.CheckConstraint("priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')", name="ck_todos_4"),
    )


def downgrade() -> None:
    op.drop_table("todos")
