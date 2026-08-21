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

# If the pinned base image lacks a CA bundle, temporarily disable APT HTTPS
# peer verification only for the ca-certificates bootstrap. APT repository
# signatures still authenticate package metadata and packages; the override is
# removed and package lists are refreshed before installing the main toolchain.
RUN set -eux; \
    rm -f /etc/apt/sources.list.d/zulu.list; \
    if [ -f /etc/apt/sources.list ]; then \
        awk ' \
            BEGIN { ubuntu_snapshot_uri = "[[:space:]]https?://(archive|security)\\.ubuntu\\.com/ubuntu/?[[:space:]]" } \
            /^[[:space:]]*#/ { print; next } \
            $0 ~ /^[[:space:]]*deb(-src)?[[:space:]]+/ && $0 ~ ubuntu_snapshot_uri { \
                if ($0 ~ /^[[:space:]]*deb(-src)?[[:space:]]+\[/) { \
                    if ($0 !~ /\[[^]]*snapshot=yes[^]]*\]/) { \
                        sub(/\[/, "[snapshot=yes "); \
                    } \
                } else { \
                    sub(/^[[:space:]]*deb(-src)?[[:space:]]+/, "&[snapshot=yes] "); \
                } \
            } \
            { print } \
        ' /etc/apt/sources.list > /etc/apt/sources.list.snapshot; \
        mv /etc/apt/sources.list.snapshot /etc/apt/sources.list; \
        awk ' \
            BEGIN { ubuntu_snapshot_uri = "[[:space:]]https?://(archive|security)\\.ubuntu\\.com/ubuntu/?[[:space:]]" } \
            /^[[:space:]]*#/ { next } \
            $0 ~ /^[[:space:]]*deb(-src)?[[:space:]]+/ && $0 ~ ubuntu_snapshot_uri && $0 !~ /\[[^]]*snapshot=yes[^]]*\]/ { \
                print "Legacy Ubuntu apt source must be snapshot-pinned: " $0 > "/dev/stderr"; \
                exit 1; \
            } \
        ' /etc/apt/sources.list; \
    fi; \
    if [ -f /etc/apt/sources.list.d/ubuntu.sources ]; then \
        sed -i '/^Signed-By:/a Snapshot: yes' /etc/apt/sources.list.d/ubuntu.sources; \
    fi; \
    printf 'APT::Snapshot "%s";\n' "$UBUNTU_APT_SNAPSHOT" > /etc/apt/apt.conf.d/50snapshot; \
    if [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then \
        printf 'Acquire::https::Verify-Peer "false";\n' > /etc/apt/apt.conf.d/49bootstrap-no-verify-peer; \
        apt-get update; \
        apt-get install -y --no-install-recommends ca-certificates; \
        rm -f /etc/apt/apt.conf.d/49bootstrap-no-verify-peer; \
        rm -rf /var/lib/apt/lists/*; \
    fi; \
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
