FROM fedora:43 AS build-setup

ARG TAG_NAME=v10.0.4
ARG BISQ_REPO=https://github.com/bisq-network/bisq.git
ARG BITCOIND_REPO=https://github.com/bisq-network/bitcoind.git

ENV TAG_NAME=${TAG_NAME}
ENV BISQ_REPO=${BISQ_REPO}
ENV BITCOIND_REPO=${BITCOIND_REPO}

RUN dnf install -y --setopt=install_weak_deps=False \
  https://cdn.azul.com/zulu/bin/zulu21.52.203-ca-jdk21.0.12.1-linux.x86_64.rpm \
  git \
  rpm-build \
  && dnf clean all

FROM build-setup AS repo-cloner
RUN git clone --depth 1 --branch $TAG_NAME $BISQ_REPO bisq

WORKDIR /bisq
RUN git config submodule.bitcoind.url $BITCOIND_REPO && \
    git submodule update --init

FROM repo-cloner AS builder
ENTRYPOINT ["./gradlew", "--no-daemon", "--stacktrace"]
CMD [":desktop:reproducibleRpm"]
