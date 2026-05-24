# UrlShortener

Generated TypeScript service (TypeScript + Express + Prisma + MySQL).

## Quick start

```bash
cp .env.example .env
docker compose up --build
curl http://localhost:8080/health
```

## Local dev

```bash
npm install
npx prisma generate
npm run typecheck
DATABASE_URL='mysql://url_shortener:url_shortener@localhost:3306/url_shortener' npm run dev
```

## Database migrations

Prisma manages the schema in `prisma/schema.prisma`. After editing the schema, run:

```bash
npx prisma migrate dev --name describe-the-change
```
