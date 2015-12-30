#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit versions
fullVersion=0.3.2.3
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
    -BjvmProperties=-Djava.net.preferIPv4Stack=true

rm "gui/deploy/Bitsquare.html"
rm "gui/deploy/Bitsquare.jnlp"
mv "gui/deploy/bundles/Bitsquare-$fullVersion.deb" "gui/deploy/Bitsquare-$fullVersion.deb"

cd package/linux