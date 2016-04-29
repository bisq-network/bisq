#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit versions
fullVersion=0.4.6
jarFile="/home/bitsquare/Desktop/sf_vm_shared_ubuntu14_32bit/Bitsquare-$fullVersion.jar"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$fullVersion \
    -Bcategory=Office,Finance \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles $jarFile \
    -srcfiles package/linux/LICENSE \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare

rm gui/deploy/Bitsquare.html
rm gui/deploy/Bitsquare.jnlp
rm gui/deploy/LICENSE
mv "gui/deploy/bundles/bitsquare-$fullVersion.deb" "gui/deploy/Bitsquare-32bit-$fullVersion.deb"
rmdir gui/deploy/bundles
cp "gui/deploy/Bitsquare-32bit-$fullVersion.deb" "/home/bitsquare/Desktop/sf_vm_shared_ubuntu14_32bit/Bitsquare-32bit-$fullVersion.deb"
cp "gui/deploy/Bitsquare-32bit-$fullVersion.deb" "/home/bitsquare/Desktop/Bitsquare-32bit-$fullVersion.deb"

cd package/linux