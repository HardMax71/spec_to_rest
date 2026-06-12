.PHONY: help install run test lint typecheck migrate migrate-up migrate-down docker-up docker-down test-conformance test-conformance-docker clean

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

migrate-up:              ## Apply pending alembic migrations (upgrade head)
	uv run alembic upgrade head

migrate-down:            ## Roll back the most recent alembic migration (downgrade -1)
	uv run alembic downgrade -1

migrate: migrate-up      ## Alias for migrate-up

docker-up:               ## Bring up all services
	docker compose up --build -d

docker-down:             ## Stop all services and remove volumes
	docker compose down -v

test-conformance:        ## Run all 3 test layers against an already-running service (PROFILE=smoke|thorough|exhaustive)
	@if [ ! -f tests/run_conformance.py ]; then \
		echo "tests/run_conformance.py not found — this project was compiled with --no-tests. Re-compile (test emission is default-on) to generate the conformance suite. Skipping."; \
	else \
		uv run python tests/run_conformance.py $(PROFILE); \
	fi

test-conformance-docker: ## Boot service via docker compose, run conformance, tear down (PROFILE=...)
	@set -e; \
	cleanup() { docker compose down -v >/dev/null 2>&1 || true; }; \
	trap cleanup EXIT; \
	uv sync --all-extras; \
	export ADMIN_TOKEN="$${ADMIN_TOKEN:-$$(openssl rand -hex 32)}"; \
	docker compose up --build -d; \
	timeout 60 bash -c 'until curl -fsS http://localhost:8000/health >/dev/null; do sleep 2; done'; \
	$(MAKE) test-conformance PROFILE=$(PROFILE)

clean:                   ## Remove caches
	rm -rf .ruff_cache .mypy_cache .pytest_cache __pycache__ results/
