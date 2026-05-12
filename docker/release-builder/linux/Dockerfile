# linux/amd64 manifest for docker.io/azul/zulu-openjdk:21.0.6.
FROM --platform=linux/amd64 azul/zulu-openjdk:21.0.6@sha256:00a833d7f08952a436332df8c2ccced19e62e88cf49791dad23653443ab23833

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=UTC \
    SOURCE_DATE_EPOCH=0 \
    HOME=/tmp/bisq-home \
    GRADLE_USER_HOME=/tmp/bisq-home/.gradle

RUN set -eux; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        binutils \
        ca-certificates \
        coreutils \
        dpkg-dev \
        fakeroot \
        file \
        findutils \
        git \
        gnupg \
        gzip \
        locales \
        openssh-client \
        rpm \
        tar \
        unzip \
        xz-utils \
        zip; \
    rm -rf /var/lib/apt/lists/*; \
    mkdir -p "$HOME" /workspace; \
    chmod 1777 "$HOME" /workspace; \
    java --version; \
    javac --version; \
    jlink --version; \
    jpackage --version

WORKDIR /workspace

CMD ["./gradlew", "verifyReleaseBuild"]
