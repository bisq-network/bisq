#!/bin/bash

cd ../../
mkdir -p deploy

set -e

# Edit version
version=0.9.0

sharedDir="/media/sf_vm_shared_ubuntu"

dir="/home/$USER/Desktop/build"
mkdir -p $dir

cp "$sharedDir/Bisq-$version.jar" "$dir/Bisq-$version.jar"
chmod o+rx "$dir/Bisq-$version.jar"

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

cp "deploy/bisq-$version.deb" "/home/$USER/Desktop/Bisq-64bit-$version.deb"
mv "deploy/bisq-$version.deb" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.deb"
rm -r deploy/
rm -r $dir

cd package/linux
