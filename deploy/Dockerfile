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
ARG ALLOY_VERSION=6.2.0
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl tar unzip \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt /opt/alloy-stdlib/util \
 && if [ "$SPEC_TO_REST_VERSION" = "latest" ]; then \
      URL="https://github.com/HardMax71/spec_to_rest/releases/latest/download/spec-to-rest-linux-amd64.tar.gz"; \
    else \
      URL="https://github.com/HardMax71/spec_to_rest/releases/download/${SPEC_TO_REST_VERSION}/spec-to-rest-linux-amd64.tar.gz"; \
    fi \
 && curl -fL "$URL" | tar -xz -C /opt \
 && chmod +x /opt/spec-to-rest \
 # Alloy looks for util/integer.als (and friends) at $java.io.tmpdir/util/*.als
 # but native-image dropped JAR resources, so the runtime extraction is a no-op.
 # Pull them from the Alloy core jar at build time and stage for the final image.
 && curl -fL -o /tmp/alloy.jar \
      "https://repo1.maven.org/maven2/org/alloytools/org.alloytools.alloy.core/${ALLOY_VERSION}/org.alloytools.alloy.core-${ALLOY_VERSION}.jar" \
 && unzip -j /tmp/alloy.jar 'models/util/*.als' -d /opt/alloy-stdlib/util/ \
 && rm /tmp/alloy.jar

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
# Alloy's verify backend reads $tmpdir/util/integer.als at parse time. 0755 so
# nonroot can traverse the dir; .als files harmlessly inherit the same mode.
COPY --from=cli-fetch --chmod=0755 /opt/alloy-stdlib/util /tmp/util
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
