#!/bin/bash

cd ../../

version="0.7.1"

target_dir="releases/$version"

linux32=build/vm/vm_shared_ubuntu14_32bit
linux64=build/vm/vm_shared_ubuntu
win32=build/vm/vm_shared_windows_32bit
win64=build/vm/vm_shared_windows

#macOS=build/vm/vm_shared_macosx
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

deb32="Bisq-32bit-$version.deb"
cp "$linux32/$deb32" "$target_dir/"

deb64="Bisq-64bit-$version.deb"
cp "$linux64/$deb64" "$target_dir/"

#rpm32="Bisq-32bit-$version.rpm"
#cp "/Users/dev/vm_shared_ubuntu14_32bit/$rpm32" "$target_dir/"

#rpm64="Bisq-64bit-$version.rpm"
#cp "/Users/dev/vm_shared_ubuntu/$rpm64" "$target_dir/"


exe="Bisq-$version.exe"
exe32="Bisq-32bit-$version.exe"
cp "$win32/bundles/$exe" "$target_dir/$exe32"
exe64="Bisq-64bit-$version.exe"
cp "$win64/bundles/$exe" "$target_dir/$exe64"
#cp "/Users/dev/vm_shared_windows/bundles/$exe" "/Users/dev/vm_shared_win10/$win64"

cd "$target_dir"

echo Create signatures
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $dmg.asc --detach-sig --armor $dmg
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $deb64.asc --detach-sig --armor $deb64
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $deb32.asc --detach-sig --armor $deb32
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $exe64.asc --detach-sig --armor $exe64
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $exe32.asc --detach-sig --armor $exe32

echo Verify signatures
gpg --digest-algo SHA256 --verify $dmg{.asc*,}
gpg --digest-algo SHA256 --verify $deb64{.asc*,}
gpg --digest-algo SHA256 --verify $deb32{.asc*,}
gpg --digest-algo SHA256 --verify $exe64{.asc*,}
gpg --digest-algo SHA256 --verify $exe32{.asc*,}

mkdir ../../build/vm/vm_shared_windows_32bit/$version
cp -r . ../../build/vm/vm_shared_windows_32bit/$version

open "."
