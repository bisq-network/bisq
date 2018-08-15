#!/bin/bash

cd ../../
mkdir -p deploy

set -e

# Edit version
version=0.7.1

dir="/media/sf_vm_shared_ubuntu"

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

# uncomment because the build VM does not support alien
#sudo alien -r -c -k deploy/bundles/bisq-$version.deb

cp "deploy/bundles/bisq-$version.deb" "/home/$USER/Desktop/Bisq-64bit-$version.deb"
mv "deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.deb"
#mv "bisq-$version-1.x86_64.rpm" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.rpm"
rm -r deploy/

cd package/linux
