FROM debian:bookworm-slim

LABEL org.opencontainers.image.source="https://github.com/HardMax71/spec_to_rest"
LABEL org.opencontainers.image.description="Compile formal behavioral specs into verified REST services"
LABEL org.opencontainers.image.licenses="MIT"

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates \
        libstdc++6 \
        zlib1g \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd -r spec && useradd -r -g spec -m -d /home/spec spec
WORKDIR /workspace
RUN chown spec:spec /workspace

ARG BINARY=spec-to-rest
COPY --chown=root:root --chmod=0755 ${BINARY} /usr/local/bin/spec-to-rest

USER spec

ENTRYPOINT ["/usr/local/bin/spec-to-rest"]
CMD ["--help"]
