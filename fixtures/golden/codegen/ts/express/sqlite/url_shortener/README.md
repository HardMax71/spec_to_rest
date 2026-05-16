# UrlShortener

Generated TypeScript service (TypeScript + Express + Prisma + SQLite).

## Quick start

```bash
docker compose up --build
curl http://localhost:8080/health
```

## Local dev

```bash
npm install
npx prisma generate
npm run typecheck
DATABASE_URL='file:./url_shortener.db' npm run dev
```

## Database migrations

Prisma manages the schema in `prisma/schema.prisma`. After editing the schema, run:

```bash
npx prisma migrate dev --name describe-the-change
```
