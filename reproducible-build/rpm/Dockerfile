FROM --platform=linux/amd64 fedora:43 AS build-setup

ARG TAG_NAME=v1.10.12
ARG BISQ_REPO=https://github.com/bisq-network/bisq.git
ARG BITCOIND_REPO=https://github.com/bisq-network/bitcoind.git

ENV TAG_NAME=${TAG_NAME}
ENV BISQ_REPO=${BISQ_REPO}
ENV BITCOIND_REPO=${BITCOIND_REPO}

ADD --checksum=sha256:2aaa2a9a43143b20ff0b28c8d0da27407b97dc40b160d9a2f60a26965815e948 https://corretto.aws/downloads/resources/21.0.13.1.2/amazon-corretto-21.0.13.1.2-linux-x64.tar.gz /tmp/corretto.tar.gz
RUN dnf install -y --setopt=install_weak_deps=False git rpm-build tar gzip findutils \
    && dnf clean all \
    && mkdir -p /opt/java/openjdk \
    && tar -xzf /tmp/corretto.tar.gz --strip-components=1 -C /opt/java/openjdk \
    && rm /tmp/corretto.tar.gz

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

FROM build-setup AS repo-cloner
RUN git clone --depth 1 --branch "$TAG_NAME" "$BISQ_REPO" bisq

WORKDIR /bisq
RUN git config submodule.bitcoind.url "$BITCOIND_REPO" && \
    git submodule update --init

FROM repo-cloner AS builder
ENTRYPOINT ["./gradlew", "--no-daemon", "--stacktrace"]
CMD [":desktop:reproducibleRpm"]
