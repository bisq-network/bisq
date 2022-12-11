#!/bin/bash

cd ../../

version="1.9.6-SNAPSHOT"

target_dir="releases/$version"

# Set BISQ_GPG_USER as environment var to the email address used for gpg signing. e.g. BISQ_GPG_USER=manfred@bitsquare.io
# Set BISQ_VM_PATH as environment var to the directory where your shared folders for virtual box are residing

vmPath=$BISQ_VM_PATH
linux64=$vmPath/vm_shared_ubuntu
win64=$vmPath/vm_shared_windows
macos=$vmPath/vm_shared_macosx

deployDir=deploy

rm -r $target_dir

mkdir -p $target_dir

# Save the current working dir (assumed to be "desktop"), and
# build the API daemon and cli distributions in the target dir.
script_working_directory=$(pwd)
# Copy the build's cli and daemon tarballs to target_dir.
cp -v ../cli/build/distributions/cli.tar $target_dir
cp -v ../daemon/build/distributions/daemon.tar $target_dir
# Copy the cli and daemon zip creation scripts to target_dir.
cp -v ../cli/package/create-cli-dist.sh $target_dir
cp -v ../daemon/package/create-daemon-dist.sh $target_dir
# Run the zip creation scripts in target_dir.
cd $target_dir
./create-cli-dist.sh $version
./create-daemon-dist.sh $version
# Clean up.
rm -v create-cli-dist.sh
rm -v create-daemon-dist.sh
# Done building cli and daemon zip files;  return to the original current working directory.
cd "$script_working_directory"

# sig key Alejandro García
cp "$target_dir/../../package/E222AA02.asc" "$target_dir/"
# sig key Gabriel Bernard
cp "$target_dir/../../package/4A133008.asc" "$target_dir/"
# sig key Christoph Atteneder
cp "$target_dir/../../package/29CDFD3B.asc" "$target_dir/"
# signing key
cp "$target_dir/../../package/signingkey.asc" "$target_dir/"

dmg="Bisq-$version.dmg"
cp "$macos/$dmg" "$target_dir/"

deb="bisq_$version-1_amd64.deb"
deb64="Bisq-64bit-$version.deb"
cp "$linux64/$deb" "$target_dir/$deb64"

rpm="bisq-$version-1.x86_64.rpm"
rpm64="Bisq-64bit-$version.rpm"
cp "$linux64/$rpm" "$target_dir/$rpm64"

exe="Bisq-$version.exe"
exe64="Bisq-64bit-$version.exe"
cp "$win64/$exe" "$target_dir/$exe64"

rpi="jar-lib-for-raspberry-pi-$version.zip"
cp "$macos/$rpi" "$target_dir/"

cli="bisq-cli-$version.zip"
daemon="bisq-daemon-$version.zip"

# create file with jar signatures
cat "$macos/desktop-$version-all-mac.jar.SHA-256" \
"$linux64/desktop-$version-all-linux.jar.SHA-256" \
"$win64/desktop-$version-all-win.jar.SHA-256" > "$target_dir/Bisq-$version.jar.txt"

sed -i '' '1 s_^_macOS: _' "$target_dir/Bisq-$version.jar.txt"
sed -i '' '2 s_^_linux: _' "$target_dir/Bisq-$version.jar.txt"
sed -i '' '3 s_^_windows: _' "$target_dir/Bisq-$version.jar.txt"

cd "$target_dir"

echo Create signatures
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$dmg.asc" --detach-sig --armor "$dmg"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$deb64.asc" --detach-sig --armor "$deb64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$rpm64.asc" --detach-sig --armor "$rpm64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$exe64.asc" --detach-sig --armor "$exe64"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$rpi.asc" --detach-sig --armor "$rpi"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$cli.asc" --detach-sig --armor "$cli"
gpg --digest-algo SHA256 --local-user "$BISQ_GPG_USER" --output "$daemon.asc" --detach-sig --armor "$daemon"

echo Verify signatures
gpg --digest-algo SHA256 --verify $dmg{.asc*,}
gpg --digest-algo SHA256 --verify $deb64{.asc*,}
gpg --digest-algo SHA256 --verify $rpm64{.asc*,}
gpg --digest-algo SHA256 --verify $exe64{.asc*,}
gpg --digest-algo SHA256 --verify $rpi{.asc*,}
gpg --digest-algo SHA256 --verify $cli{.asc*,}
gpg --digest-algo SHA256 --verify $daemon{.asc*,}

mkdir $win64/$version
cp -r . $win64/$version

open "."
