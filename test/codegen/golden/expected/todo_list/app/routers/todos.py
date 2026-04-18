from fastapi import APIRouter, Depends, HTTPException, Response
from sqlalchemy.ext.asyncio import AsyncSession

from app.database import get_session
from app.schemas.todo import (
    TodoCreate,
    TodoRead,
    TodoUpdate,
)
from app.services.todo import TodoService

router = APIRouter(tags=["todo"])


@router.post("/todos", status_code=201)
async def create_todo(
    body: TodoCreate,
    session: AsyncSession = Depends(get_session),
) -> TodoRead:
    svc = TodoService(session)
    return await svc.create_todo(body)

@router.get("/todos", status_code=200)
async def list_todos(
    session: AsyncSession = Depends(get_session),
) -> list[TodoRead]:
    svc = TodoService(session)
    return await svc.list_todos()

@router.get("/todos/{id}", status_code=200)
async def get_todo(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> TodoRead:
    svc = TodoService(session)
    result = await svc.get_todo(id)
    if result is None:
        raise HTTPException(status_code=404, detail="not found")
    return result

@router.patch("/todos/{id}", status_code=200)
async def update_todo(
    id: int,
    body: TodoUpdate,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = TodoService(session)
    return await svc.update_todo(id, body)

@router.post("/todos/{id}/start", status_code=200)
async def start_work(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = TodoService(session)
    return await svc.start_work(id)

@router.post("/todos/{id}/complete", status_code=200)
async def complete(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = TodoService(session)
    return await svc.complete(id)

@router.post("/todos/{id}/reopen", status_code=200)
async def reopen(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = TodoService(session)
    return await svc.reopen(id)

@router.post("/todos/{id}/archive", status_code=200)
async def archive(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> None:
    svc = TodoService(session)
    return await svc.archive(id)

@router.delete("/todos/{id}", status_code=204)
async def delete_todo(
    id: int,
    session: AsyncSession = Depends(get_session),
) -> Response:
    svc = TodoService(session)
    deleted = await svc.delete_todo(id)
    if not deleted:
        raise HTTPException(status_code=404, detail="not found")
    return Response(status_code=204)
