.PHONY: install build dev start test typecheck prisma-generate migrate migrate-up migrate-down

install:
	npm install

build:
	npm run build

dev:
	npm run dev

start:
	npm start

test:
	npm test

typecheck:
	npm run typecheck

prisma-generate:
	npx prisma generate

migrate-up:
	npx prisma migrate deploy

migrate-down:
	@LATEST=$$(ls -1 prisma/migrations 2>/dev/null | grep -v migration_lock | sort -V | tail -n1); \
	test -n "$$LATEST" || { echo "no migrations to roll back" >&2; exit 1; }; \
	test -f "prisma/migrations/$$LATEST/down.sql" || { echo "no down.sql for $$LATEST" >&2; exit 1; }; \
	echo "rolling back: $$LATEST"; \
	npx prisma db execute --file "prisma/migrations/$$LATEST/down.sql" --schema prisma/schema.prisma && \
	npx prisma migrate resolve --rolled-back "$$LATEST" --schema prisma/schema.prisma

migrate: migrate-up
