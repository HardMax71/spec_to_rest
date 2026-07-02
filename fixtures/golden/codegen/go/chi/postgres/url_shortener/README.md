# UrlShortener

Generated Go service (Go + chi + PostgreSQL).

## Quick start

```bash
cp .env.example .env
docker compose up --build
curl http://localhost:8080/health
curl http://localhost:8080/ready
curl http://localhost:8080/metrics
```

Traces are opt-in: set `OTEL_EXPORTER_OTLP_ENDPOINT` (for example
`http://localhost:4318`) to export OTLP spans for every request; leave it
unset and tracing stays off. `OTEL_SERVICE_NAME` overrides the default
service name.

## Local dev

```bash
go mod tidy
go test ./...
DATABASE_URL='postgres://url_shortener:url_shortener@localhost:5432/url_shortener?sslmode=disable' go run ./cmd/server
```
