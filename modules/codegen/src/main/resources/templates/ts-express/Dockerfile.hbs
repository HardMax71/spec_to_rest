FROM node:20-alpine AS builder

WORKDIR /src
COPY package.json ./
COPY package-lock.jso[n] ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npx prisma generate
RUN npm run build

FROM node:20-alpine
RUN apk add --no-cache wget && adduser -D -u 10001 app
WORKDIR /app
COPY --from=builder /src/node_modules ./node_modules
COPY --from=builder /src/dist ./dist
COPY --from=builder /src/prisma ./prisma
COPY --from=builder /src/package.json ./package.json
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost:8080/health || exit 1
ENTRYPOINT ["node", "dist/index.js"]
