#!/bin/bash

version="0.4.5"

target_dir="/Users/mk/Documents/__bitsquare/_releases/$version"

cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/Bitsquare-$version.dmg" "$target_dir/Bitsquare-$version.dmg"
cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/SeedNode-$version.jar" "$target_dir/SeedNode-$version.jar"

linux32="Bitsquare-32bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu14_32bit/$linux32" "$target_dir/$linux32"

linux64="Bitsquare-64bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu/$linux64" "$target_dir/$linux64" 

exe="Bitsquare.exe"
cp "/Users/mk/vm_shared_windows_32bit/bundles/$exe" "$target_dir/Bitsquare-32bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "$target_dir/Bitsquare-64bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "/Users/mk/vm_shared_win10/Bitsquare-64bit-$version.exe"

cp "hashes.template" "$target_dir/hashes.template"
cd "$target_dir"

MAC="Bitsquare-$version.dmg"
HASH_MAC="$(shasum -a 256 $MAC)"

DEB_64="Bitsquare-64bit-$version.deb"
HASH_DEB_64="$(shasum -a 256 $DEB_64)"

DEB_64="Bitsquare-64bit-$version.deb"
HASH_DEB_64="$(shasum -a 256 $DEB_64)"

DEB_32="Bitsquare-32bit-$version.deb"
HASH_DEB_32="$(shasum -a 256 $DEB_32)"

WIN_64="Bitsquare-64bit-$version.exe"
HASH_WIN_64="$(shasum -a 256 $WIN_64)"

WIN_32="Bitsquare-32bit-$version.exe"
HASH_WIN_32="$(shasum -a 256 $WIN_32)"

sed -e "s|HASH_MAC|$HASH_MAC|" -e "s|HASH_DEB_64|$HASH_DEB_64|" -e "s|HASH_DEB_32|$HASH_DEB_32|" -e "s|HASH_WIN_64|$HASH_WIN_64|" -e "s|HASH_WIN_32|$HASH_WIN_32|" hashes.template > hashes.txt

gpg --local-user manfred@bitsquare.io --output signed_hashes.txt --clearsign hashes.txt
gpg --verify signed_hashes.txt

rm "$target_dir/hashes.template"
rm "$target_dir/hashes.txt"

open "$target_dir"