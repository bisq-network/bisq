#!/usr/bin/env bash
# Requirements:
#   - GPG signing key has been created
# Prior to running this script:
#   - Update version below

version=0.9.5-SNAPSHOT
base_dir=$( cd "$(dirname "$0")" ; pwd -P )/../../..
package_dir=$base_dir/desktop/package
release_dir=$base_dir/desktop/release/$version

dmg=Bisq-$version.dmg
deb=Bisq-$version.deb
rpm=Bisq-$version.rpm
exe=Bisq-$version.exe

read -p "Enter email address used for gpg signing: " gpg_user

echo Creating release directory
if [ -d "$release_dir" ]; then
    rm -fr "$release_dir"
fi
mkdir -p "$release_dir"

echo Copying files to release folder
# sig key mkarrer
cp "$package_dir/F379A1C6.asc" "$release_dir"
# sig key cbeams
cp "$package_dir/5BC5ED73.asc" "$release_dir"
# sig key Christoph Atteneder
cp "$package_dir/29CDFD3B.asc" "$release_dir"
# signing key
cp "$package_dir/signingkey.asc" "$release_dir"
if [ -f "$package_dir/macosx/$dmg" ]; then
    cp "$package_dir/macosx/$dmg" "$release_dir"
    cp "$package_dir/macosx/$dmg.txt" "$release_dir"
fi
if [ -f "$package_dir/linux/$deb" ]; then
    cp "$package_dir/linux/$deb" "$release_dir"
    cp "$package_dir/linux/$deb.txt" "$release_dir"
fi
if [ -f "$package_dir/linux/$rpm" ]; then
    cp "$package_dir/linux/$rpm" "$release_dir"
    cp "$package_dir/linux/$rpm.txt" "$release_dir"
fi
if [ -f "$package_dir/windows/$exe" ]; then
    cp "$package_dir/windows/$exe" "$release_dir"
    cp "$package_dir/windows/$exe.txt" "$release_dir"
fi

echo Creating signatures
if [ -f "$release_dir/$dmg" ]; then
    gpg --digest-algo SHA256 --local-user $gpg_user --output "$release_dir/$dmg.asc" --detach-sig --armor "$release_dir/$dmg"
fi
if [ -f "$release_dir/$deb" ]; then
    gpg --digest-algo SHA256 --local-user $gpg_user --output "$release_dir/$deb.asc" --detach-sig --armor "$release_dir/$deb"
fi
if [ -f "$release_dir/$rpm" ]; then
    gpg --digest-algo SHA256 --local-user $gpg_user --output "$release_dir/$rpm.asc" --detach-sig --armor "$release_dir/$rpm"
fi
if [ -f "$release_dir/$exe" ]; then
    gpg --digest-algo SHA256 --local-user $gpg_user --output "$release_dir/$exe.asc" --detach-sig --armor "$release_dir/$exe"
fi

echo Verifying signatures
if [ -f "$release_dir/$dmg" ]; then
    gpg --digest-algo SHA256 --verify "$release_dir/$dmg.asc"
fi
if [ -f "$release_dir/$deb" ]; then
    gpg --digest-algo SHA256 --verify "$release_dir/$deb.asc"
fi
if [ -f "$release_dir/$rpm" ]; then
    gpg --digest-algo SHA256 --verify "$release_dir/$rpm.asc"
fi
if [ -f "$release_dir/$exe" ]; then
    gpg --digest-algo SHA256 --verify "$release_dir/$exe.asc"
fi

echo Done!
