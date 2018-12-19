#!/bin/bash

cd $(dirname $0)/../../

mkdir -p deploy

set -e

version="0.9.1"

cd ..
./gradlew :desktop:build -x test shadowJar
cd desktop

EXE_JAR=build/libs/desktop-$version-all.jar

# we need to strip out Java 9 module configuration used in the fontawesomefx library as it causes the javapackager to stop,
# because of this existing module information, although it is not used as a module.
echo Unzipping jar to delete module config
tmp=build/libs/tmp
unzip -o -q $EXE_JAR -d $tmp

# Sometimes $tmp/module-info.class is not available. TODO check why and if still needed
rm -f $tmp/module-info.class

rm $EXE_JAR
echo Zipping jar again without module config
cd $tmp; zip -r -q -X "../desktop-$version-all.jar" *
cd ../../../; rm -rf $tmp

echo SHA 256 before stripping jar file:
shasum -a256 $EXE_JAR | awk '{print $1}'

# We make a deterministic jar by stripping out comments with date, etc.
# jar file created from https://github.com/ManfredKarrer/tools
java -jar ./package/macosx/tools-1.0.jar $EXE_JAR

echo SHA 256 after stripping jar file to get a deterministic jar:
shasum -a256 $EXE_JAR | awk '{print $1}' | tee deploy/Bisq-$version.jar.txt

#vmPath=/Users/christoph/Documents/Workspaces/Java
vmPath=/Volumes
linux64=$vmPath/vm_shared_ubuntu
win64=$vmPath/vm_shared_windows

mkdir -p $linux64 $win64

cp $EXE_JAR "deploy/Bisq-$version.jar"

# copy app jar to VM shared folders
cp $EXE_JAR "$linux64/Bisq-$version.jar"
# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp $EXE_JAR "$win64/Bisq.jar"

# Copy packager scripts to VM. No need to checkout the source as we only are interested in the build scripts.
rm -rf "$linux64/package"
rm -rf "$win64/package"

mkdir -p "$linux64/package"
mkdir -p "$win64/package"

cp -r package/linux "$linux64/package"
cp -r package/windows "$win64/package"


if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(/usr/libexec/java_home)
fi

# Open jdk does not has the java packager.
# JAVA_HOME=/Library/Java/JavaVirtualMachines/oracle_jdk-10.0.2.jdk/Contents/Home

echo "Using JAVA_HOME: $JAVA_HOME"

$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=io.bisq.CAT \
    -Bmac.CFBundleName=Bisq \
    -Bicon=package/macosx/Bisq.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir deploy \
    -srcdir deploy \
    -srcfiles "Bisq-$version.jar" \
    -appclass bisq.desktop.app.BisqAppMain \
    -outfile Bisq \
    -v

open deploy

cd package/macosx
