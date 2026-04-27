.PHONY: help install run test lint typecheck migrate docker-up docker-down test-conformance test-conformance-docker clean

PROFILE ?= thorough

help:                    ## Show available commands
	@awk 'BEGIN {FS = ":.*##"} /^[a-zA-Z_-]+:.*?##/ {printf "  %-24s %s\n", $$1, $$2}' $(MAKEFILE_LIST)

install:                 ## Install dependencies with uv
	uv sync --all-extras

run:                     ## Run dev server on :8000
	uv run uvicorn app.main:app --reload --host 0.0.0.0 --port 8000

test:                    ## Run pytest
	uv run pytest

lint:                    ## Run ruff
	uv run ruff check app/ tests/

typecheck:               ## Run mypy
	uv run mypy app/

migrate:                 ## Apply alembic migrations
	uv run alembic upgrade head

docker-up:               ## Bring up all services
	docker compose up --build -d

docker-down:             ## Stop all services and remove volumes
	docker compose down -v

test-conformance:        ## Run all 3 test layers against an already-running service (PROFILE=smoke|thorough|exhaustive)
	uv run python tests/run_conformance.py $(PROFILE)

test-conformance-docker: ## Boot service via docker compose, run conformance, tear down (PROFILE=...)
	@set -e; \
	cleanup() { docker compose down -v >/dev/null 2>&1 || true; }; \
	trap cleanup EXIT; \
	uv sync --all-extras; \
	ENABLE_TEST_ADMIN=1 docker compose up --build -d; \
	timeout 60 bash -c 'until curl -fsS http://localhost:8000/health >/dev/null; do sleep 2; done'; \
	$(MAKE) test-conformance PROFILE=$(PROFILE)

clean:                   ## Remove caches
	rm -rf .ruff_cache .mypy_cache .pytest_cache __pycache__ results/
