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

# Generate the plist from the template
sed "s|JAR_NAME_STRING_GOES_HERE|$buildVersion.jar|" package/mac/Info.template.plist >package/mac/Info.plist


mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$buildVersion.jar

java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

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
    -srcfiles gui/updatefx/builds/processed/$buildVersion.jar \
    -appclass io.bitsquare.app.gui.BitsquareAppMain \
    -outfile Bitsquare
    
cd package/mac