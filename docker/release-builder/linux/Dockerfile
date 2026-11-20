# Keep Ubuntu's package snapshots for the release tools; pin the Corretto JDK separately.
FROM --platform=linux/amd64 ubuntu:24.04@sha256:77eb101c607d6e3d4f5ad0c8a02c2136c689b98aba1f359e6502ba517e5c57ec

# Corretto 21.0.13.1.2 includes the fix for CVE-2026-114938.
ADD --checksum=sha256:2aaa2a9a43143b20ff0b28c8d0da27407b97dc40b160d9a2f60a26965815e948 https://corretto.aws/downloads/resources/21.0.13.1.2/amazon-corretto-21.0.13.1.2-linux-x64.tar.gz /tmp/corretto.tar.gz
RUN mkdir -p /opt/java/openjdk \
    && tar -xzf /tmp/corretto.tar.gz --strip-components=1 -C /opt/java/openjdk \
    && rm /tmp/corretto.tar.gz

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ARG UBUNTU_APT_SNAPSHOT=20261111T000000Z

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
