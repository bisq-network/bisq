#!/bin/bash

cd ../../
mkdir -p deploy

set -e

version="0.6.7"

mvn clean package verify -DskipTests -Dmaven.javadoc.skip=true

linux32=/Users/dev/vm_shared_ubuntu14_32bit
linux64=/Users/dev/vm_shared_ubuntu
win32=/Users/dev/vm_shared_windows_32bit
win64=/Users/dev/vm_shared_windows

cp target/shaded.jar "deploy/Bisq-$version.jar"

# copy app jar to VM shared folders
cp target/shaded.jar "$linux32/Bisq-$version.jar"
cp target/shaded.jar "$linux64/Bisq-$version.jar"
# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp target/shaded.jar "$win32/Bisq.jar"
cp target/shaded.jar "$win64/Bisq.jar"

# copy bouncycastle jars to VM shared folders
lib1=bcpg-jdk15on.jar
cp target/lib/$lib1 "$linux32/$lib1"
cp target/lib/$lib1 "$linux64/$lib1"
cp target/lib/$lib1 "$win32/$lib1"
cp target/lib/$lib1 "$win64/$lib1"

lib2=bcprov-jdk15on.jar
cp target/lib/$lib2 "$linux32/$lib2"
cp target/lib/$lib2 "$linux64/$lib2"
cp target/lib/$lib2 "$win32/$lib2"
cp target/lib/$lib2 "$win64/$lib2"


echo "Using JAVA_HOME: $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=bisq \
    -Bmac.CFBundleName=Bisq \
    -Bicon=package/osx/Bisq.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir deploy \
    -srcfiles "deploy/Bisq-$version.jar" \
    -srcfiles "target/lib/bcpg-jdk15on.jar" \
    -srcfiles "target/lib/bcprov-jdk15on.jar" \
    -appclass bisq.desktop.app.BisqAppMain \
    -outfile Bisq


# TODO <Class-Path>lib/bcpg-jdk15on.jar lib/bcprov-jdk15on.jar</Class-Path> not included in build
# when we have support for security manager we use that
#     \
#    -BjvmOptions=-Djava.security.manager \
#    -BjvmOptions=-Djava.security.debug=failure \
#    -BjvmOptions=-Djava.security.policy=file:bisq.policy
#     -srcfiles "core/src/main/resources/bisq.policy" \

rm "deploy/Bisq.html"
rm "deploy/Bisq.jnlp"

mv "deploy/bundles/Bisq-$version.dmg" "deploy/Bisq-$version.dmg"
rm -r "deploy/bundles"

open deploy

cd package/osx
