#!/bin/bash

cd ../../

set -e

# Extract the version numbers. 
majorVersion=$( sed -n 's/^.*final int MAJOR_VERSION = //p' core/src/main/java/io/bitsquare/app/Version.java )
minorVersion=$( sed -n 's/^.*final int MINOR_VERSION = //p' core/src/main/java/io/bitsquare/app/Version.java )
# PatchVersion is used for ever increasing integer at UpdateFX. fullVersion contains major and minor version + patchVersion 
patchVersion=$( sed -n 's/^.*final int PATCH_VERSION = //p' core/src/main/java/io/bitsquare/app/Version.java )

# remove trailing;
majorVersion="${majorVersion:0:${#majorVersion}-1}"
minorVersion="${minorVersion:0:${#minorVersion}-1}"
patchVersion="${patchVersion:0:${#patchVersion}-1}"

fullVersion=$( sed -n 's/^.*final String VERSION = "//p' core/src/main/java/io/bitsquare/app/Version.java )
# remove trailing ";
fullVersion=$majorVersion.$minorVersion.$patchVersion

echo majorVersion = $majorVersion
echo minorVersion = $minorVersion
echo patchVersion = $patchVersion
echo fullVersion = $fullVersion

# Generate the plist from the template
sed "s|JAR_NAME_STRING_GOES_HERE|$patchVersion.jar|" package/mac/Info.template.plist >package/mac/Info.plist


mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$patchVersion.jar

java -jar ./updatefx/updatefx-app-1.6.jar --url=http://bitsquare.io/updateFX/ gui/updatefx
# using trezor
#java -jar ./updatefx/updatefx-app-1.6.jar --url=http://bitsquare.io/updateFX/ gui/updatefx --trezor 

$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$fullVersion \
    -Bmac.CFBundleIdentifier=io.bitsquare \
    -Bmac.CFBundleName=Bitsquare \
    -Bicon=package/mac/Bitsquare.icns \
    -Bruntime="$JAVA_HOME/../../" \
    -native dmg \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles gui/updatefx/builds/processed/$patchVersion.jar \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare
    
cd package/mac