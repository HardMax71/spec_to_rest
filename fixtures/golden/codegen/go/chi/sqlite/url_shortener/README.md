# UrlShortener

Generated Go service (Go + chi + SQLite).

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
DATABASE_URL='file:url_shortener.db?cache=shared&_pragma=foreign_keys(1)' go run ./cmd/server
```
