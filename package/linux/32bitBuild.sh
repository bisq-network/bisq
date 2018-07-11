#!/bin/bash

cd ../../
mkdir -p deploy

set -e

# Edit version
version=0.7.1

dir="/media/sf_vm_shared_ubuntu14_32bit"

# Note: fakeroot needs to be installed on Linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=contact@bisq.network \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir deploy \
    -srcfiles "$dir/Bisq-$version.jar" \
    -srcfiles package/linux/LICENSE \
    -appclass bisq.desktop.app.BisqAppMain \
    -BjvmOptions=-Xss1280k \
    -outfile Bisq


# sudo alien -r -c -k deploy/bundles/bisq-$version.deb

cp "deploy/bundles/bisq-$version.deb" "/home/$USER/Desktop/Bisq-32bit-$version.deb"
mv "deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu14_32bit/Bisq-32bit-$version.deb"

# mv "bisq-$version-1.i386.rpm" "/media/sf_vm_shared_ubuntu14_32bit/Bisq-32bit-$version.rpm"
rm -r deploy/

cd package/linux
