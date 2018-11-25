#!/bin/bash

cd $(dirname $0)/../../

mkdir -p deploy

set -e

version="0.8.0"

cd ..
./gradlew :desktop:build -x test shadowJar
cd desktop

EXE_JAR=build/libs/desktop-$version-all.jar

# we need to strip out Java 9 module configuration used in the fontawesomefx library as it causes the javapackager to stop,
# because of this existing module information, although it is not used as a module.
echo Unzipping jar to delete module config
tmp=build/libs/tmp
unzip -o -q $EXE_JAR -d $tmp
rm $tmp/module-info.class
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

# vmPath=/Volumes
vmPath=/Users/dev
linux32=$vmPath/vm_shared_ubuntu14_32bit
linux64=$vmPath/vm_shared_ubuntu
win32=$vmPath/vm_shared_windows_32bit
win64=$vmPath/vm_shared_windows

mkdir -p $linux32 $linux64 $win32 $win64

cp $EXE_JAR "deploy/Bisq-$version.jar"

# copy app jar to VM shared folders
cp $EXE_JAR "$linux32/Bisq-$version.jar"
cp $EXE_JAR "$linux64/Bisq-$version.jar"
# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp $EXE_JAR "$win32/Bisq.jar"
cp $EXE_JAR "$win64/Bisq.jar"

# copy bouncycastle jars to VM shared folders
# bc_lib1=bcpg-jdk15on-1.56.jar
# cp build/app/lib/$bc_lib1 "$linux32/$bc_lib1"
# cp build/app/lib/$bc_lib1 "$linux64/$bc_lib1"
# cp build/app/lib/$bc_lib1 "$win32/$bc_lib1"
# cp build/app/lib/$bc_lib1 "$win64/$bc_lib1"

# bc_lib2=bcprov-jdk15on-1.56.jar
# cp build/app/lib/$bc_lib2 "$linux32/$bc_lib2"
# cp build/app/lib/$bc_lib2 "$linux64/$bc_lib2"
# cp build/app/lib/$bc_lib2 "$win32/$bc_lib2"
# cp build/app/lib/$bc_lib2 "$win64/$bc_lib2"

# Copy packager scripts to VM. No need to checkout the source as we only are interested in the build scripts.
rm -rf "$linux32/package"
rm -rf "$linux64/package"
rm -rf "$win32/package"
rm -rf "$win64/package"

mkdir -p "$linux32/package"
mkdir -p "$linux64/package"
mkdir -p "$win32/package"
mkdir -p "$win64/package"

cp -r package/linux "$linux32/package"
cp -r package/linux "$linux64/package"
cp -r package/windows "$win32/package"
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
    -outfile Bisq

open deploy

cd package/macosx
