#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

version="0.6.0"

mvn clean package verify -DskipTests -Dmaven.javadoc.skip=true

# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp gui/target/shaded.jar "gui/deploy/Bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu/Bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows/Bisq.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu14_32bit/Bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows_32bit/Bisq.jar"

echo "Using JAVA_HOME: $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=io.bisq \
    -Bmac.CFBundleName=Bisq \
    -Bicon=package/osx/Bisq.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir gui/deploy \
    -srcfiles "gui/deploy/Bisq-$version.jar" \
    -appclass io.bisq.gui.app.BisqAppMain \
    -outfile Bisq


# TODO <Class-Path>lib/bcpg-jdk15on.jar lib/bcprov-jdk15on.jar</Class-Path> not included in build
# when we have support for security manager we use that
#     \
#    -BjvmOptions=-Djava.security.manager \
#    -BjvmOptions=-Djava.security.debug=failure \
#    -BjvmOptions=-Djava.security.policy=file:bisq.policy
#     -srcfiles "core/src/main/resources/bisq.policy" \

rm "gui/deploy/Bisq.html"
rm "gui/deploy/Bisq.jnlp"

mv "gui/deploy/bundles/Bisq-$version.dmg" "gui/deploy/Bisq-$version.dmg"
rm -r "gui/deploy/bundles"

open gui/deploy

cd package/osx
