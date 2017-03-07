#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

version="0.4.9.9"

mvn clean package verify -DskipTests -Dmaven.javadoc.skip=true

cp gui/target/shaded.jar "gui/deploy/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu14_32bit/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows_32bit/Bitsquare-$version.jar"

echo "Using JAVA_HOME: $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=io.bitsquare \
    -Bmac.CFBundleName=Bitsquare \
    -Bicon=package/mac/Bitsquare.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles "gui/deploy/Bitsquare-$version.jar" \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare \

rm "gui/deploy/Bitsquare.html"
rm "gui/deploy/Bitsquare.jnlp"

mv "gui/deploy/bundles/Bitsquare-$version.dmg" "gui/deploy/Bitsquare-$version.dmg"
rm -r "gui/deploy/bundles"

cd package/mac