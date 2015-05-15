#!/bin/bash

cd ../../

set -e

# Extract the version number. patchVersion is used for ever increasing integer at UpdateFX. fullVersion contains major and minor version + patchVersion 
patchVersion=$( sed -n 's/^.*final int PATCH_VERSION = //p' core/src/main/java/io/bitsquare/app/Version.java )

# remove trailing;
buildVersion="${buildVersion:0:${#buildVersion}-1}"
echo patchVersion = $patchVersion

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$patchVersion.jar
java -jar ./updatefx/updatefx-app-1.6.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

cd package/mac