# UrlShortener

Generated Go service (Go + chi + MySQL).

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
DATABASE_URL='url_shortener:url_shortener@tcp(localhost:3306)/url_shortener?parseTime=true' go run ./cmd/server
```
