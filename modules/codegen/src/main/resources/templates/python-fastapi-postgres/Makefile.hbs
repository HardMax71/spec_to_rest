.PHONY: help install run test lint typecheck migrate docker-up docker-down clean

help:         ## Show available commands
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ {printf "  %-12s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

install:      ## Install dependencies with uv
	uv sync --all-extras

run:          ## Run dev server on :8000
	uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

test:         ## Run pytest
	uv run pytest

lint:         ## Run ruff
	uv run ruff check app/ tests/

typecheck:    ## Run mypy
	uv run mypy app/

migrate:      ## Apply alembic migrations
	uv run alembic upgrade head

docker-up:    ## Bring up all services
	docker compose up --build -d

docker-down:  ## Stop all services and remove volumes
	docker compose down -v

clean:        ## Remove caches
	rm -rf .ruff_cache .mypy_cache .pytest_cache __pycache__
