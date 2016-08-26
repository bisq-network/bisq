#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

version="0.4.9.5"

mvn clean package -DskipTests -Dmaven.javadoc.skip=true

cp gui/target/shaded.jar "gui/deploy/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_ubuntu/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_windows/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_ubuntu14_32bit/Bitsquare-$version.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_windows_32bit/Bitsquare-$version.jar"

cp seednode/target/SeedNode.jar "gui/deploy/SeedNode-$version.jar"

cp jdkfix/target/jdkfix-$version.jar "/Users/mk/vm_shared_ubuntu/jdkfix-$version.jar"
cp jdkfix/target/jdkfix-$version.jar "/Users/mk/vm_shared_windows/jdkfix-$version.jar"
cp jdkfix/target/jdkfix-$version.jar "/Users/mk/vm_shared_ubuntu14_32bit/jdkfix-$version.jar"
cp jdkfix/target/jdkfix-$version.jar "/Users/mk/vm_shared_windows_32bit/jdkfix-$version.jar"

echo "Using JAVA_HOME: $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BjvmOptions=-Xbootclasspath/a:"jdkfix-$version.jar":"../PlugIns/Java.runtime/Contents/Home/jre/lib/ext/jfxrt.jar" \
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
    -srcfiles "gui/deploy/Bitsquare-$version.jar:jdkfix/target/jdkfix-$version.jar" \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare \
    -BjvmProperties=-Djava.net.preferIPv4Stack=true

rm "gui/deploy/Bitsquare.html"
rm "gui/deploy/Bitsquare.jnlp"

mv "gui/deploy/bundles/Bitsquare-$version.dmg" "gui/deploy/Bitsquare-$version.dmg"
rm -r "gui/deploy/bundles"

cd package/mac