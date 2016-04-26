#!/bin/bash

version="0.4.4"

target_dir="/Users/mk/Documents/__bitsquare/_releases/$version"

cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/bundles/Bitsquare-0.4.4.dmg" "$target_dir/Bitsquare-0.4.4.dmg "
cp "/Users/mk/Documents/_intellij/bitsquare/gui/deploy/SeedNode-0.4.4.jar" "$target_dir/SeedNode-0.4.4.jar"


linux32="Bitsquare-32bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu14_32bit/$linux32" "$target_dir/$linux32 "

linux64="Bitsquare-64bit-$version.deb"
cp "/Users/mk/vm_shared_ubuntu/$linux64" "$target_dir/$linux64" 

exe="Bitsquare.exe"
cp "/Users/mk/vm_shared_windows_32bit/bundles/$exe" "$target_dir/Bitsquare-32bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "$target_dir/Bitsquare-64bit-$version.exe"
cp "/Users/mk/vm_shared_windows/bundles/$exe" "/Users/mk/vm_shared_win10/Bitsquare-64bit-$version.exe"

open "$target_dir"