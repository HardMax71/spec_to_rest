# UrlShortener

Generated Go service (Go + chi + PostgreSQL).

## Quick start

```bash
cp .env.example .env
docker compose up --build
curl http://localhost:8080/health
```

## Local dev

```bash
go mod tidy
go test ./...
DATABASE_URL='postgres://url_shortener:url_shortener@localhost:5432/url_shortener?sslmode=disable' go run ./cmd/server
```
