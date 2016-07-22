#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit versions
<<<<<<< Temporary merge branch 1
fullVersion=0.4.9
=======
fullVersion=0.4.8.1
>>>>>>> Temporary merge branch 2
jarFile="/home/bitsquare/Desktop/sf_vm_shared_ubuntu14_32bit/Bitsquare-$fullVersion.jar"

jarFile="/media/sf_vm_shared_ubuntu14_32bit/Bitsquare-$version.jar"
jdkfixFile="/media/sf_vm_shared_ubuntu14_32bit/jdkfix-$version.jar"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BjvmOptions=-Xbootclasspath/a:"jdkfix-$version.jar":"../runtime/lib/ext/jfxrt.jar" \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Internet \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles $jarFile:$jdkfixFile \
    -srcfiles package/linux/LICENSE \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare

rm gui/deploy/Bitsquare.html
rm gui/deploy/Bitsquare.jnlp
rm gui/deploy/LICENSE
mv "gui/deploy/bundles/bitsquare-$version.deb" "gui/deploy/Bitsquare-32bit-$version.deb"
rmdir gui/deploy/bundles
cp "gui/deploy/Bitsquare-32bit-$version.deb" "/media/sf_vm_shared_ubuntu14_32bit/Bitsquare-32bit-$version.deb"
cp "gui/deploy/Bitsquare-32bit-$version.deb" "/home/bitsquare/Desktop/Bitsquare-32bit-$version.deb"

cd package/linux