#!/bin/bash

version="0.4.8"

target_dir="/Users/mk/Documents/__bitsquare/_releases/$version"

mac="Bitsquare-$version.dmg"
cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/$mac" "$target_dir/"
cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/SeedNode-$version.jar" "$target_dir/"

deb32="Bitsquare-32bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu14_32bit/$deb32" "$target_dir/"

deb64="Bitsquare-64bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu/$deb64" "$target_dir/" 

exe="Bitsquare.exe"
win32="Bitsquare-32bit-$version.exe"
cp "/Users/mk/vm_shared_windows_32bit/bundles/$exe" "$target_dir/$win32"
win64="Bitsquare-64bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "$target_dir/$win64"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "/Users/mk/vm_shared_win10/$win64"

cd "$target_dir"

shasum -a 256 "$mac" "$deb64" "$deb32" "$win64" "$win32" > sha256_hashes.txt

gpg --local-user manfred@bitsquare.io --output signed_sha256_hashes.txt --clearsign sha256_hashes.txt

gpg --verify signed_sha256_hashes.txt

rm "$target_dir/sha256_hashes.txt"

open "$target_dir"
