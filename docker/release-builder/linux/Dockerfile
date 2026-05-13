# linux/amd64 manifest for docker.io/azul/zulu-openjdk:21.0.6.
FROM --platform=linux/amd64 azul/zulu-openjdk:21.0.6@sha256:00a833d7f08952a436332df8c2ccced19e62e88cf49791dad23653443ab23833

ARG UBUNTU_APT_SNAPSHOT=20260501T000000Z

ENV DEBIAN_FRONTEND=noninteractive \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    TZ=UTC \
    SOURCE_DATE_EPOCH=0 \
    HOME=/tmp/bisq-home \
    GRADLE_USER_HOME=/tmp/bisq-home/.gradle

RUN set -eux; \
    if [ -f /etc/apt/sources.list ]; then \
        sed -i -E 's|^deb (http://(archive|security).ubuntu.com/ubuntu/?[[:space:]])|deb [snapshot=yes] \1|' /etc/apt/sources.list; \
    fi; \
    if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then \
        sed -i '/^Signed-By:/a Snapshot: yes' /etc/apt/sources.list.d/ubuntu.sources; \
    fi; \
    printf 'APT::Snapshot "%s";\n' "$UBUNTU_APT_SNAPSHOT" > /etc/apt/apt.conf.d/50snapshot; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        binutils \
        ca-certificates \
        coreutils \
        cpio \
        dpkg \
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
