#!/bin/bash

cd ../../

set -e

# Extract the version number. buildVersion is used for ever increasing integer at UpdateFX. fullVersion contains major and minor version + buildVersion 
buildVersion=$( sed -n 's/^.*final int BUILD_VERSION = //p' gui/src/main/java/io/bitsquare/app/gui/UpdateProcess.java )
# remove trailing;
buildVersion="${buildVersion:0:${#buildVersion}-1}"
fullVersion=$( sed -n 's/^.*final String VERSION = "//p' gui/src/main/java/io/bitsquare/app/gui/BitsquareAppMain.java )
# remove trailing ";
fullVersion="${fullVersion:0:${#fullVersion}-2}"

echo buildVersion = $buildVersion
echo fullVersion = $fullVersion

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$buildVersion.jar

# edit url
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$fullVersion \
    -Bcategory=Finance \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles gui/updatefx/builds/processed/$buildVersion.jar \
    -appclass io.bitsquare.app.gui.BitsquareAppMain \
    -outfile Bitsquare

cd package/linux

# TODO: Figure out where LICENSE file goes so distros don't complain about "low quality" packages.
