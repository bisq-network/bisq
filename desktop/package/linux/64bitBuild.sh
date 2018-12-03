#!/bin/bash

cd ../../
mkdir -p deploy

set -e

# Edit version
version=0.9.0

dir="/media/sf_vm_shared_ubuntu"

# Note: fakeroot needs to be installed on Linux

# TODO: need to add the licenses back again as soon as it is working with our build setup
#-BlicenseFile=LICENSE \
#-srcfiles package/linux/LICENSE \

$JAVA_HOME/bin/javapackager \
    -deploy \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=contact@bisq.network \
    -BlicenseType=GPLv3 \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir deploy \
    -srcdir $dir \
    -srcfiles "Bisq-$version.jar" \
    -appclass bisq.desktop.app.BisqAppMain \
    -BjvmOptions=-Xss1280k \
    -outfile Bisq \
    -v

# uncomment because the build VM does not support alien
#sudo alien -r -c -k deploy/bundles/bisq-$version.deb

cp "deploy/bundles/bisq-$version.deb" "/home/$USER/Desktop/Bisq-64bit-$version.deb"
mv "deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.deb"
rm -r deploy/

cd package/linux
