ARG SPEC_TO_REST_VERSION=latest

FROM node:22-bookworm-slim AS web-build
# /repo layout mirrors the source tree so run-cli-snippets.mjs's `REPO_ROOT = path.resolve(docs, "..")` resolves.
WORKDIR /repo
COPY docs/package.json docs/package-lock.json ./docs/
WORKDIR /repo/docs
# --ignore-scripts: package.json's postinstall (fumadocs-mdx) needs content/ which isn't COPY'd yet.
RUN npm ci --ignore-scripts

WORKDIR /repo
COPY docs/        ./docs/
COPY fixtures/spec ./fixtures/spec

WORKDIR /repo/docs
ENV NEXT_TELEMETRY_DISABLED=1
RUN npm run postinstall && npm run build

FROM debian:bookworm-slim AS cli-fetch
ARG SPEC_TO_REST_VERSION
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl tar \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt \
 && if [ "$SPEC_TO_REST_VERSION" = "latest" ]; then \
      URL="https://github.com/HardMax71/spec_to_rest/releases/latest/download/spec-to-rest-linux-amd64.tar.gz"; \
    else \
      URL="https://github.com/HardMax71/spec_to_rest/releases/download/${SPEC_TO_REST_VERSION}/spec-to-rest-linux-amd64.tar.gz"; \
    fi \
 && curl -fL "$URL" | tar -xz -C /opt \
 && chmod +x /opt/spec-to-rest

FROM node:22-bookworm-slim
ARG SPEC_TO_REST_VERSION
LABEL org.opencontainers.image.source="https://github.com/HardMax71/spec_to_rest" \
      org.opencontainers.image.title="spec-to-rest docs + playground" \
      org.opencontainers.image.version="${SPEC_TO_REST_VERSION}"

RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates libstdc++6 zlib1g tini \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 65532 nonroot \
 && useradd  --system --uid 65532 --gid nonroot --home /tmp --shell /sbin/nologin nonroot

WORKDIR /app
COPY --from=cli-fetch /opt/spec-to-rest                  /usr/local/bin/spec-to-rest
COPY --from=web-build /repo/docs/.next/standalone        /app/
COPY --from=web-build /repo/docs/.next/static            /app/.next/static
COPY --from=web-build /repo/docs/public                  /app/public

ENV PORT=8080 \
    HOSTNAME=0.0.0.0 \
    NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    NO_COLOR=1 \
    SPEC_TO_REST_BIN=/usr/local/bin/spec-to-rest

EXPOSE 8080
USER nonroot
ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["node", "server.js"]
