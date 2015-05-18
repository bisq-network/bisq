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

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$patchVersion.jar
java -jar ./updatefx/updatefx-app-1.6.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

cd package/mac