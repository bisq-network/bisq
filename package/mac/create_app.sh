#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

fullVersion="0.4.9"

mvn clean package -DskipTests -Dmaven.javadoc.skip=true

cp gui/target/shaded.jar "gui/deploy/Bitsquare-$fullVersion.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_ubuntu/Bitsquare-$fullVersion.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_windows/Bitsquare-$fullVersion.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_ubuntu14_32bit/Bitsquare-$fullVersion.jar"
cp gui/target/shaded.jar "/Users/mk/vm_shared_windows_32bit/Bitsquare-$fullVersion.jar"

cp seednode/target/SeedNode.jar "gui/deploy/SeedNode.jar"

echo "Using $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BjvmOptions=-Xbootclasspath/a:"jdkfix-$fullVersion.jar":"$JAVA_HOME/jre/lib/ext/jfxrt.jar" \
    -BappVersion=$fullVersion \
    -Bmac.CFBundleIdentifier=io.bitsquare \
    -Bmac.CFBundleName=Bitsquare \
    -Bicon=package/mac/Bitsquare.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles "gui/deploy/Bitsquare-$fullVersion.jar:jdkfix/target/jdkfix-$fullVersion.jar" \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare \
    -BjvmProperties=-Djava.net.preferIPv4Stack=true

rm "gui/deploy/Bitsquare.html"
rm "gui/deploy/Bitsquare.jnlp"

mv "gui/deploy/bundles/Bitsquare-$fullVersion.dmg" "gui/deploy/Bitsquare-$fullVersion.dmg"
rm -r "gui/deploy/bundles"

mv "gui/deploy/SeedNode.jar" "gui/deploy/SeedNode-$fullVersion.jar"

cd package/mac
