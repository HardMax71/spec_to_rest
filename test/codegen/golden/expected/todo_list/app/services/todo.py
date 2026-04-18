from sqlalchemy import delete as sa_delete, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.models.todo import Todo
from app.schemas.todo import (
    CreateTodoRequest,
    TodoRead,
    UpdateTodoRequest,
)


class TodoService:
    def __init__(self, session: AsyncSession) -> None:
        self._session = session

    async def create_todo(self, body: CreateTodoRequest) -> None:
        raise NotImplementedError(
            "create operation 'CreateTodo' — implement in M4+"
        )

    async def list_todos(self) -> list[TodoRead]:
        result = await self._session.execute(select(Todo))
        rows = result.scalars().all()
        return [TodoRead.model_validate(row) for row in rows]

    async def get_todo(self, id: int) -> TodoRead | None:
        result = await self._session.execute(
            select(Todo).where(Todo.id == id)
        )
        row = result.scalar_one_or_none()
        return TodoRead.model_validate(row) if row is not None else None

    async def update_todo(self, id: int, body: UpdateTodoRequest) -> None:
        raise NotImplementedError(
            "partial_update operation 'UpdateTodo' — implement in M4+"
        )

    async def start_work(self, id: int) -> None:
        raise NotImplementedError(
            "transition operation 'StartWork' — implement in M4+"
        )

    async def complete(self, id: int) -> None:
        raise NotImplementedError(
            "transition operation 'Complete' — implement in M4+"
        )

    async def reopen(self, id: int) -> None:
        raise NotImplementedError(
            "transition operation 'Reopen' — implement in M4+"
        )

    async def archive(self, id: int) -> None:
        raise NotImplementedError(
            "transition operation 'Archive' — implement in M4+"
        )

    async def delete_todo(self, id: int) -> bool:
        result = await self._session.execute(
            sa_delete(Todo).where(Todo.id == id)
        )
        return result.rowcount > 0
