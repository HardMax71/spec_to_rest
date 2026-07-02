# UrlShortener

Generated Go service (Go + chi + MySQL).

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
DATABASE_URL='url_shortener:url_shortener@tcp(localhost:3306)/url_shortener?parseTime=true' go run ./cmd/server
```
