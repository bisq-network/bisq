#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

version="0.6.2"

mvn clean package verify -DskipTests -Dmaven.javadoc.skip=true

linux32=/Users/dev/vm_shared_ubuntu14_32bit
linux64=/Users/dev/vm_shared_ubuntu
win32=/Users/dev/vm_shared_windows_32bit
win64=/Users/dev/vm_shared_windows

cp gui/target/shaded.jar "gui/deploy/Bisq-$version.jar"

# copy app jar to VM shared folders
cp gui/target/shaded.jar "$linux32/Bisq-$version.jar"
cp gui/target/shaded.jar "$linux64/Bisq-$version.jar"
# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp gui/target/shaded.jar "$win32/Bisq.jar"
cp gui/target/shaded.jar "$win64/Bisq.jar"

# copy bouncycastle jars to VM shared folders
lib1=bcpg-jdk15on.jar
cp gui/target/lib/$lib1 "$linux32/$lib1"
cp gui/target/lib/$lib1 "$linux64/$lib1"
cp gui/target/lib/$lib1 "$win32/$lib1"
cp gui/target/lib/$lib1 "$win64/$lib1"

lib2=bcprov-jdk15on.jar
cp gui/target/lib/$lib2 "$linux32/$lib2"
cp gui/target/lib/$lib2 "$linux64/$lib2"
cp gui/target/lib/$lib2 "$win32/$lib2"
cp gui/target/lib/$lib2 "$win64/$lib2"


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
    -srcfiles "gui/target/lib/bcpg-jdk15on.jar" \
    -srcfiles "gui/target/lib/bcprov-jdk15on.jar" \
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
