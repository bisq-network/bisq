#!/bin/bash
set -e

cd ../../../

version="1.10.3"

target_dir="desktop/releases/$version"

# Set BISQ_GPG_USER as environment var to the email address used for gpg signing. e.g. BISQ_GPG_USER=manfred@bitsquare.io
# Set BISQ_VM_PATH as environment var to the directory where your shared folders for virtual box are residing
# Optional: Set BISQ_MACOS_X86_64_PATH and BISQ_MACOS_AARCH64_PATH to override the macOS build artifact directories.

vmPath=$BISQ_VM_PATH
linux64=$vmPath/vm_shared_ubuntu
win64=$vmPath/vm_shared_windows
macos=$vmPath/vm_shared_macosx
macos_x86_64=${BISQ_MACOS_X86_64_PATH:-$macos}
macos_aarch64=${BISQ_MACOS_AARCH64_PATH:-$vmPath/vm_shared_macosx_aarch64}

deployDir=deploy

rm -rf "$target_dir"

mkdir -p "$target_dir"

# make sure the releases are ready
./gradlew cli:build
./gradlew daemon:build

# Save the current working dir (assumed to be "root"), and
# build the API daemon and cli distributions in the target dir.
script_working_directory="$(pwd)"
# Copy the build's cli and daemon tarballs to target_dir.
cp -v ./cli/build/distributions/cli.tar $target_dir
cp -v ./daemon/build/distributions/daemon.tar $target_dir

DIR=(`pwd`)
# Execute and copy results
cd ./cli/package
./create-cli-dist.sh $version
cd $DIR
cp -v ./cli/package/* $target_dir
rm -vf ./cli/package/bisq*

cd ./daemon/package
./create-daemon-dist.sh $version
cd $DIR
cp -v ./daemon/package/* $target_dir
rm -vf ./daemon/package/bisq*

echo "cd into $target_dir"
cd $target_dir
# Clean up.
rm -v create-cli-dist.sh
rm -v create-daemon-dist.sh
# Done building cli and daemon zip files;  return to the original current working directory.
echo "cd into $script_working_directory"

cd "$script_working_directory"

# sig key Alejandro García
cp -v "./desktop/package/E222AA02.asc" "$target_dir/"
# sig key Gabriel Bernard
cp -v "./desktop/package/4A133008.asc" "$target_dir/"
# sig key Henrik Jannsen
cp -v "./desktop/package/387C8307.asc" "$target_dir/"
# signing key
cp -v "./desktop/package/signingkey.asc" "$target_dir/"

dmg_x86_64="Bisq-x86_64-$version.dmg"
dmg_aarch64="Bisq-aarch64-$version.dmg"
cp "$macos_x86_64/$dmg_x86_64" "$target_dir/"
cp "$macos_aarch64/$dmg_aarch64" "$target_dir/"

deb="bisq_$version-1_amd64.deb"
deb64="Bisq-64bit-$version.deb"
cp "$linux64/$deb" "$target_dir/$deb64"

rpm="bisq-$version-1.x86_64.rpm"
rpm64="Bisq-64bit-$version.rpm"
cp "$linux64/$rpm" "$target_dir/$rpm64"

exe="Bisq-$version.exe"
exe64="Bisq-64bit-$version.exe"
cp "$win64/$exe" "$target_dir/$exe64"

cli="bisq-cli-$version.zip"
daemon="bisq-daemon-$version.zip"
jar_txt="Bisq-$version.jar.txt"

# create file with jar checksums
cat "$macos_x86_64/desktop-$version-all-mac-x86_64.jar.SHA-256" \
"$macos_aarch64/desktop-$version-all-mac-aarch64.jar.SHA-256" \
"$linux64/desktop-$version-all-linux.jar.SHA-256" \
"$win64/desktop-$version-all-win.jar.SHA-256" > "$target_dir/$jar_txt"
if [[ ! -s "$target_dir/$jar_txt" ]]; then
    echo "Missing or empty jar checksum file: $target_dir/$jar_txt" >&2
    exit 1
fi

cd "$script_working_directory/$target_dir" || exit 1

echo Create signatures
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$dmg_x86_64.asc" --detach-sig --armor "$dmg_x86_64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$dmg_aarch64.asc" --detach-sig --armor "$dmg_aarch64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$deb64.asc" --detach-sig --armor "$deb64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$rpm64.asc" --detach-sig --armor "$rpm64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$exe64.asc" --detach-sig --armor "$exe64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$cli.asc" --detach-sig --armor "$cli"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$daemon.asc" --detach-sig --armor "$daemon"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$jar_txt.asc" --detach-sig --armor "$jar_txt"

echo Verify signatures
gpg --digest-algo SHA256 --verify $dmg_x86_64{.asc*,}
gpg --digest-algo SHA256 --verify $dmg_aarch64{.asc*,}
gpg --digest-algo SHA256 --verify $deb64{.asc*,}
gpg --digest-algo SHA256 --verify $rpm64{.asc*,}
gpg --digest-algo SHA256 --verify $exe64{.asc*,}
gpg --digest-algo SHA256 --verify $cli{.asc*,}
gpg --digest-algo SHA256 --verify $daemon{.asc*,}
gpg --digest-algo SHA256 --verify $jar_txt{.asc*,}

mkdir -p "$win64/$version"
cp -r . "$win64/$version"

open "./desktop/releases/$version"
