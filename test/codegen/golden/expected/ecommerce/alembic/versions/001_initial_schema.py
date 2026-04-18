"""Initial schema for OrderService.

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
        "products",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("sku", sa.Text(), nullable=False),
        sa.Column("name", sa.Text(), nullable=False),
        sa.Column("price", sa.Integer(), nullable=False),
        sa.Column("description", sa.Text(), nullable=True),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('length(name) >= 1', name="ck_products_0"),
        sa.CheckConstraint('price > 0', name="ck_products_1"),
    )

    op.create_table(
        "line_items",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("product_sku", sa.Text(), nullable=False),
        sa.Column("quantity", sa.Integer(), nullable=False),
        sa.Column("unit_price", sa.Integer(), nullable=False),
        sa.Column("line_total", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('id > 0', name="ck_line_items_0"),
        sa.CheckConstraint('unit_price > 0', name="ck_line_items_1"),
    )

    op.create_table(
        "orders",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("customer_id", sa.Integer(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("items", postgresql.JSONB(), server_default=sa.text("'[]'::jsonb"), nullable=False),
        sa.Column("subtotal", sa.Integer(), nullable=False),
        sa.Column("tax", sa.Integer(), nullable=False),
        sa.Column("total", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("shipped_at", sa.DateTime(timezone=True), nullable=True),
        sa.Column("delivered_at", sa.DateTime(timezone=True), nullable=True),
        sa.CheckConstraint("status IN ('DRAFT', 'PLACED', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCELLED', 'RETURNED')", name="ck_orders_0"),
    )

    op.create_table(
        "payments",
        sa.Column("id", sa.Integer(), primary_key=True, nullable=False),
        sa.Column("order_id", sa.Integer(), nullable=False),
        sa.Column("amount", sa.Integer(), nullable=False),
        sa.Column("status", sa.Text(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('id > 0', name="ck_payments_0"),
        sa.CheckConstraint("status IN ('PENDING', 'AUTHORIZED', 'CAPTURED', 'REFUNDED', 'FAILED')", name="ck_payments_1"),
        sa.CheckConstraint('amount > 0', name="ck_payments_2"),
    )

    op.create_table(
        "inventory_entries",
        sa.Column("id", sa.BigInteger(), primary_key=True, autoincrement=True, nullable=False),
        sa.Column("sku", sa.Text(), nullable=False),
        sa.Column("available", sa.Integer(), nullable=False),
        sa.Column("reserved", sa.Integer(), nullable=False),
        sa.Column("created_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.Column("updated_at", sa.DateTime(timezone=True), server_default=sa.func.now(), nullable=False),
        sa.CheckConstraint('available >= 0', name="ck_inventory_entries_0"),
        sa.CheckConstraint('reserved >= 0', name="ck_inventory_entries_1"),
    )


def downgrade() -> None:
    op.drop_table("inventory_entries")
    op.drop_table("payments")
    op.drop_table("orders")
    op.drop_table("line_items")
    op.drop_table("products")
