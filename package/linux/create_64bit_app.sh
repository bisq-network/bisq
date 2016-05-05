#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit versions
fullVersion=0.4.6
jarFile="/home/mk/Desktop/sf_vm_shared_ubuntu/Bitsquare-$fullVersion.jar"

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
mv "gui/deploy/bundles/bitsquare-$fullVersion.deb" "gui/deploy/Bitsquare-$fullVersion.deb"
rmdir gui/deploy/bundles
cp "gui/deploy/Bitsquare-$fullVersion.deb" "/home/mk/Desktop/sf_vm_shared_ubuntu/Bitsquare-64bit-$fullVersion.deb"
cp "gui/deploy/Bitsquare-$fullVersion.deb" "/home/mk/Desktop/Bitsquare-64bit-$fullVersion.deb"

cd package/linux