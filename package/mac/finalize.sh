#!/bin/bash

version="0.4.9.5"

target_dir="/Users/mk/Documents/__bitsquare/_releases/$version"
src_dir="/Users/mk/Documents/_intellij/bitsquare"

mkdir -p $target_dir

mac="Bitsquare-$version.dmg"
cp "$src_dir/gui/deploy/$mac" "$target_dir/"
cp "$src_dir/gui/deploy/SeedNode-$version.jar" "$target_dir/"

deb32="Bitsquare-32bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu14_32bit/$deb32" "$target_dir/"

deb64="Bitsquare-64bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu/$deb64" "$target_dir/" 

#rpm32="Bitsquare-32bit-$version.rpm"
#cp "/Users/mk/vm_shared_ubuntu14_32bit/$rpm32" "$target_dir/"

#rpm64="Bitsquare-64bit-$version.rpm"
#cp "/Users/mk/vm_shared_ubuntu/$rpm64" "$target_dir/" 


exe="Bitsquare-$version.exe"
win32="Bitsquare-32bit-$version.exe"
cp "/Users/mk/vm_shared_windows_32bit/bundles/$exe" "$target_dir/$win32"
win64="Bitsquare-64bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "$target_dir/$win64"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "/Users/mk/vm_shared_win10/$win64"

cd "$target_dir"

#shasum -a 256 "$mac" "$deb64" "$deb32" "$rpm64" "$rpm32" "$win64" "$win32" > sha256_hashes.txt
shasum -a 256 "$mac" "$deb64" "$deb32" "$win64" "$win32" > sha256_hashes.txt

gpg --digest-algo SHA256 --local-user manfred@bitsquare.io --output signed_sha256_hashes.txt --clearsign sha256_hashes.txt

gpg --digest-algo SHA256 --verify signed_sha256_hashes.txt

rm "$target_dir/sha256_hashes.txt"

open "$target_dir"
