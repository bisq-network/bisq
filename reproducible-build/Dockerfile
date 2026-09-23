FROM azul/zulu-openjdk:21.0.10-21.48 AS build-setup

ARG TAG_NAME=v10.0.4
ARG BISQ_REPO=https://github.com/bisq-network/bisq.git
ARG BITCOIND_REPO=https://github.com/bisq-network/bitcoind.git

ENV TAG_NAME=${TAG_NAME}
ENV BISQ_REPO=${BISQ_REPO}
ENV BITCOIND_REPO=${BITCOIND_REPO}

RUN apt-get update && apt-get install -y --no-install-recommends \
  git \
  && rm -rf /var/lib/apt/lists/*

FROM build-setup AS repo-cloner
RUN git clone --depth 1 --branch $TAG_NAME $BISQ_REPO bisq

WORKDIR /bisq
RUN git config submodule.bitcoind.url $BITCOIND_REPO && \
    git submodule update --init

FROM repo-cloner AS builder
ENTRYPOINT ["./gradlew", "--no-daemon"]
CMD ["build"]
