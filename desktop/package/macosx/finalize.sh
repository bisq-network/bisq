#!/bin/bash

cd ../../

version="1.1.2-SNAPSHOT"

target_dir="releases/$version"

vmPath=/Users/christoph/Documents/Workspaces/Java
#vmPath=/Volumes
linux64=$vmPath/vm_shared_ubuntu/desktop/package/linux
win64=$vmPath/vm_shared_windows/desktop/package/windows

macOS=deploy

# Set BISQ_GPG_USER as environment var to the email address used for gpg signing. e.g. BISQ_GPG_USER=manfred@bitsquare.io

rm -r $target_dir

mkdir -p $target_dir

# sig key mkarrer
cp "$target_dir/../../package/F379A1C6.asc" "$target_dir/"
# sig key cbeams
cp "$target_dir/../../package/5BC5ED73.asc" "$target_dir/"
# sig key Christoph Atteneder
cp "$target_dir/../../package/29CDFD3B.asc" "$target_dir/"
# signing key
cp "$target_dir/../../package/signingkey.asc" "$target_dir/"
# hash of jar file
cp "deploy/Bisq-$version.jar.txt" "$target_dir/"

dmg="Bisq-$version.dmg"
cp "$macOS/$dmg" "$target_dir/"

deb="Bisq-$version.deb"
deb64="Bisq-64bit-$version.deb"
cp "$linux64/$deb" "$target_dir/$deb64"

rpm="Bisq-$version.rpm"
rpm64="Bisq-64bit-$version.rpm"
cp "$linux64/$rpm" "$target_dir/$rpm64"

exe="Bisq-$version.exe"
exe64="Bisq-64bit-$version.exe"
cp "$win64/$exe" "$target_dir/$exe64"

cd "$target_dir"

echo Create signatures
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $dmg.asc --detach-sig --armor $dmg
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $deb64.asc --detach-sig --armor $deb64
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $rpm64.asc --detach-sig --armor $rpm64
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $exe64.asc --detach-sig --armor $exe64

echo Verify signatures
gpg --digest-algo SHA256 --verify $dmg{.asc*,}
gpg --digest-algo SHA256 --verify $deb64{.asc*,}
gpg --digest-algo SHA256 --verify $rpm64{.asc*,}
gpg --digest-algo SHA256 --verify $exe64{.asc*,}

mkdir $win64/$version
cp -r . $win64/$version

open "."
