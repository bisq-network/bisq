#!/bin/bash

cd ../../
mkdir -p deploy

set -e

version="0.6.7"

./gradlew build

linux32=build/vm/vm_shared_ubuntu14_32bit
linux64=build/vm/vm_shared_ubuntu
win32=build/vm/vm_shared_windows_32bit
win64=build/vm/vm_shared_windows

mkdir -p $linux32 $linux64 $win32 $win64

cp build/libs/bisq-desktop.jar "deploy/Bisq-$version.jar"

# copy app jar to VM shared folders
cp build/libs/bisq-desktop.jar "$linux32/Bisq-$version.jar"
cp build/libs/bisq-desktop.jar "$linux64/Bisq-$version.jar"
# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp build/libs/bisq-desktop.jar "$win32/Bisq.jar"
cp build/libs/bisq-desktop.jar "$win64/Bisq.jar"

if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(/usr/libexec/java_home)
fi

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
