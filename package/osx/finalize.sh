#!/bin/bash

cd ../../

version="0.7.0"

target_dir="releases/$version"

linux32=build/vm/vm_shared_ubuntu14_32bit
linux64=build/vm/vm_shared_ubuntu
win32=build/vm/vm_shared_windows_32bit
win64=build/vm/vm_shared_windows
macOS=build/vm/vm_shared_macosx
gpg_user="manfred@bitsquare.io"

rm -r $target_dir

mkdir -p $target_dir

# new signing key
#cp "$target_dir/../7D20BB32.asc" "$target_dir/"

# sig key mkarrer
cp "$target_dir/../F379A1C6.asc" "$target_dir/"
# sig key cbeams
cp "$target_dir/../5BC5ED73.asc" "$target_dir/"
# sig key Christoph Atteneder
cp "$target_dir/../29CDFD3B.asc" "$target_dir/"
# signing key
cp "$target_dir/../signingkey.asc" "$target_dir/"

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


gpg --digest-algo SHA256 --local-user $gpg_user --output $dmg.asc --detach-sig --armor $dmg
gpg --digest-algo SHA256 --local-user $gpg_user --output $deb64.asc --detach-sig --armor $deb64
gpg --digest-algo SHA256 --local-user $gpg_user --output $deb32.asc --detach-sig --armor $deb32
gpg --digest-algo SHA256 --local-user $gpg_user --output $exe64.asc --detach-sig --armor $exe64
gpg --digest-algo SHA256 --local-user $gpg_user --output $exe32.asc --detach-sig --armor $exe32

gpg --digest-algo SHA256 --verify $dmg{.asc*,}
gpg --digest-algo SHA256 --verify $deb64{.asc*,}
gpg --digest-algo SHA256 --verify $deb32{.asc*,}
gpg --digest-algo SHA256 --verify $exe64{.asc*,}
gpg --digest-algo SHA256 --verify $exe32{.asc*,}

#cp -r $target_dir /Users/dev/vm_shared_windows_32bit/

open "."
