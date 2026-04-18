"""Initial schema for EdgeCases.

Revision ID: 001
Create Date: 2026-04-18
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
        "bases",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('id > 0', name="ck_bases_0"),
    )

    op.create_table(
        "children",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("score", sa.Float(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('score >= 0', name="ck_children_0"),
        sa.CheckConstraint('score <= 100', name="ck_children_1"),
    )


def downgrade() -> None:
    op.drop_table("children")
    op.drop_table("bases")
