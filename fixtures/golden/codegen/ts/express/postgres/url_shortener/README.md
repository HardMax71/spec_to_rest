# UrlShortener

Generated TypeScript service (TypeScript + Express + Prisma + PostgreSQL).

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
DATABASE_URL='postgresql://url_shortener:url_shortener@localhost:5432/url_shortener?schema=public' npm run dev
```

## Database migrations

Prisma manages the schema in `prisma/schema.prisma`. After editing the schema, run:

```bash
npx prisma migrate dev --name describe-the-change
```
