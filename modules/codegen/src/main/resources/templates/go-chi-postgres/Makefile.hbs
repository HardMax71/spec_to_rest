.PHONY: build run test fmt vet tidy migrate-up migrate-down

build:
	go build ./...

run:
	go run ./cmd/server

test:
	go test ./...

fmt:
	gofmt -s -w .

vet:
	go vet ./...

tidy:
	go mod tidy

migrate-up:
	@command -v migrate >/dev/null 2>&1 || { echo >&2 "install golang-migrate first"; exit 1; }
	migrate -path migrations -database "$$DATABASE_URL" up

migrate-down:
	@command -v migrate >/dev/null 2>&1 || { echo >&2 "install golang-migrate first"; exit 1; }
	migrate -path migrations -database "$$DATABASE_URL" down
