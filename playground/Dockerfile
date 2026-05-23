ARG SPEC_TO_REST_VERSION=v2.1.0

FROM golang:1.23-alpine AS wrapper-build
WORKDIR /src
COPY go.mod ./
COPY server.go ./
RUN CGO_ENABLED=0 GOOS=linux go build -trimpath -ldflags="-s -w" -o /out/playground .

FROM debian:bookworm-slim AS cli-fetch
ARG SPEC_TO_REST_VERSION
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl tar \
 && rm -rf /var/lib/apt/lists/* \
 && mkdir -p /opt/spec-to-rest \
 && curl -fL "https://github.com/HardMax71/spec_to_rest/releases/download/${SPEC_TO_REST_VERSION}/spec-to-rest-linux-amd64.tar.gz" \
    | tar -xz -C /opt/spec-to-rest \
 && chmod +x /opt/spec-to-rest/spec-to-rest

FROM debian:bookworm-slim
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates libstdc++6 zlib1g tini \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --system --gid 65532 nonroot \
 && useradd  --system --uid 65532 --gid nonroot --home /tmp --shell /sbin/nologin nonroot

COPY --from=cli-fetch    /opt/spec-to-rest/spec-to-rest /usr/local/bin/spec-to-rest
COPY --from=wrapper-build /out/playground               /usr/local/bin/playground

ENV PORT=8080 \
    SPEC_TO_REST_BIN=/usr/local/bin/spec-to-rest \
    NO_COLOR=1

EXPOSE 8080
USER nonroot
WORKDIR /tmp

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["/usr/local/bin/playground"]
