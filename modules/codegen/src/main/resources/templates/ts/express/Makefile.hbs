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
	@test -n "$(NAME)" || { \
	  echo "Prisma has no native rollback; this target undoes one specific migration." >&2; \
	  echo "Usage: make migrate-down NAME=<migration-folder>" >&2; \
	  echo "" >&2; \
	  echo "Available migrations:" >&2; \
	  ls -1 prisma/migrations 2>/dev/null | grep -v migration_lock | sed 's/^/  /' >&2 || true; \
	  exit 1; \
	}
	@test -f "prisma/migrations/$(NAME)/down.sql" || { echo "no down.sql at prisma/migrations/$(NAME)/down.sql" >&2; exit 1; }
	npx prisma db execute --file "prisma/migrations/$(NAME)/down.sql" --schema prisma/schema.prisma
	@echo "DELETE FROM _prisma_migrations WHERE migration_name = '$(NAME)';" | npx prisma db execute --stdin --schema prisma/schema.prisma

migrate: migrate-up
