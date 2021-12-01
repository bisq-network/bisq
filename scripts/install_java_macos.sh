#!/bin/bash

# Install OpenJDK 11.0.2 on macOS

set -eu

# Download and install locations
JDK_URL=https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_osx-x64_bin.tar.gz
JAVA_HOME_DIR=/Library/Java/JavaVirtualMachines/openjdk-11.0.2.jdk


OS=$(uname)
if [[ $OS != Darwin ]]
then
    echo This script supports macOS only >&2
    exit 1
fi

command -v curl >/dev/null || { echo "cURL is not available" >&2; exit 1; }
command -v tar >/dev/null || { echo "tar is not available" >&2; exit 1; }

sudo_exec () {
    if [[ $EUID -eq 0 ]]
    then
        "$@"
    else
        sudo "$@"
    fi
}

JDK_FILENAME=$(basename "$JDK_URL")

tmpdir=$(mktemp -d)
trap -- 'rm -rf "$tmpdir"' EXIT

mkdir "$tmpdir/JAVA_HOME_DIR"
curl -L -o "$tmpdir/$JDK_FILENAME" "$JDK_URL"
tar -xf "$tmpdir/$JDK_FILENAME" -C "$tmpdir/JAVA_HOME_DIR" --strip-components=2

if [[ -d "$tmpdir/JAVA_HOME_DIR/Contents" ]]
then
    sudo_exec rm -rf "$JAVA_HOME_DIR"
    sudo_exec mkdir -p "$(dirname "$JAVA_HOME_DIR")"
    sudo_exec mv "$tmpdir/JAVA_HOME_DIR" "$JAVA_HOME_DIR"
else
    echo "Error extracting archive contents" >&2
    exit 1
fi

echo "Java has been installed in $JAVA_HOME_DIR"
echo "To start using it, please set/update your 'JAVA_HOME' and 'PATH' environment variables like so:"
echo
echo "    export JAVA_HOME=\"$JAVA_HOME_DIR/Contents/Home\""
echo "    export PATH=\"$JAVA_HOME_DIR/Contents/Home/bin:\$PATH\""
echo
echo "Consider adding the above lines to one of your personal initialization files."
echo "(~/.bashrc, ~/.bash_profile, ~/.profile, or similar)."

export JAVA_HOME="$JAVA_HOME_DIR/Contents/Home"
export PATH="$JAVA_HOME_DIR/Contents/Home/bin":$PATH
