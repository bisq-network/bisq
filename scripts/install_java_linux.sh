#!/bin/bash

# Install OpenJDK 11.0.2 on Linux

set -eu

# Download and install locations
JDK_URL=https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz
JAVA_HOME_DIR=/usr/lib/jvm/openjdk-11.0.2

alpine_openjdk_package=openjdk11
alpine_openjdk_location=/usr/lib/jvm/java-11-openjdk


OS=$(uname)
if [ "$OS" != Linux ]
then
    echo This script supports Linux only >&2
    exit 1
fi

PACKAGE_MANAGER=
for tool in apk yum pacman emerge zypper apt-get dnf
do
    if command -v $tool >/dev/null
    then
        PACKAGE_MANAGER=$tool
        break
    fi
done

if [ -z "$PACKAGE_MANAGER" ]
then
    echo "Unknown OS" >&2
fi

missing=
for cmd in curl tar gzip
do
    if ! command -v $cmd >/dev/null
    then
        missing="${missing+$missing }$cmd"
        if [ "$cmd" = curl ]
        then
            missing="$missing ca-certificates"
        fi
    fi
done

sudo_exec () {
    if [ "${EID-500}" -eq 0 ] || [ "${HOME-/home}" = /root ]
    then
        "$@"
    elif command -v sudo
    then
        sudo "$@"
    else
        echo "Can't execute with elevated priviliges: $*" >&2
        exit 1
    fi
}

# Install missing packages
if [ -n "$missing" ]
then
    case "$PACKAGE_MANAGER" in
        apk)
            : no need to install missing packages, because
            : we will install OpenJDK using apk
            ;;
        pacman)
            sudo_exec pacman -Syy --noconfirm "$missing"
            ;;
        apt-get)
            sudo_exec apt-get update
            # shellcheck disable=SC2086
            sudo_exec apt-get install -y --no-install-recommends $missing
            ;;
        dnf|emerge|yum|zypper)
            sudo_exec "$PACKAGE_MANAGER" update
            # shellcheck disable=SC2086
            sudo_exec "$PACKAGE_MANAGER" install -y $missing
            ;;
        *)
            echo "The following packages are missing from your system: $missing" >&2
            echo "Please install these packages before proceeding" >&2
            exit 1;
            ;;
    esac
fi

if [ "$PACKAGE_MANAGER" = apk ]
then
    if sudo_exec apk add --no-cache ${alpine_openjdk_package}
    then
        echo "Installed Java to $alpine_openjdk_location"
        echo "To start using 'javac', add $alpine_openjdk_location/bin to your PATH:"
        echo "export PATH=$alpine_openjdk_location/bin:\$PATH"
    fi
    exit
fi


JDK_FILENAME=$(basename "$JDK_URL")
tmpdir=$(mktemp -d)
trap -- 'rm -rf "$tmpdir"' EXIT

mkdir "$tmpdir/JAVA_HOME_DIR"
curl -L -o "$tmpdir/$JDK_FILENAME" "$JDK_URL"
tar -xf "$tmpdir/$JDK_FILENAME" -C "$tmpdir/JAVA_HOME_DIR" --strip-components=1

if [ -d "$tmpdir/JAVA_HOME_DIR/bin" ]
then
    sudo_exec rm -rf "$JAVA_HOME_DIR"
    sudo_exec mkdir -p "$(dirname "$JAVA_HOME_DIR")"
    sudo_exec mv "$tmpdir/JAVA_HOME_DIR" "$JAVA_HOME_DIR"
else
    echo "Error extracting archive contents" >&2
    exit 1
fi

echo "Java has been installed in $JAVA_HOME_DIR"

if command -v update-alternatives >/dev/null
then
    update-alternatives --install /usr/bin/java java "$JAVA_HOME_DIR/bin/java" 2000
    update-alternatives --install /usr/bin/javac javac "$JAVA_HOME_DIR/bin/javac" 2000
    update-alternatives --set java "$JAVA_HOME_DIR/bin/java"
    update-alternatives --set javac "$JAVA_HOME_DIR/bin/javac"

    echo "and configured as the default JDK using 'update-alternatives'."
    echo "If you need to change to another JDK later, you can do so like so:"
    echo "    update-alternatives --config java"
    echo "    update-alternatives --config javac"
else
    echo "To start using it, please set/update your 'JAVA_HOME' and 'PATH' environment variables like so:"
    echo
    echo "export JAVA_HOME=\"$JAVA_HOME_DIR\""
    echo "export PATH=\"$JAVA_HOME_DIR/bin:\$PATH\""
    echo
    echo "Consider adding the above lines to one of your personal initialization files"
    echo " like ~/.bashrc, ~/.bash_profile, ~/.profile, or similar."
fi
