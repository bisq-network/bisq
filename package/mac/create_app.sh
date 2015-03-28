#!/bin/bash

cd ../../

set -e

# Extract the version number. buildVersion is used for ever increasing integer at UpdateFX. fullVersion contains major and minor version + buildVersion 
buildVersion=$( sed -n 's/^.*final int BUILD_VERSION = //p' core/src/main/java/io/bitsquare/app/UpdateProcess.java )
# remove trailing;
buildVersion="${buildVersion:0:${#buildVersion}-1}"
fullVersion=$( sed -n 's/^.*final String VERSION = "//p' core/src/main/java/io/bitsquare/app/BitsquareAppMain.java )
# remove trailing ";
fullVersion="${fullVersion:0:${#fullVersion}-2}".$buildVersion

echo buildVersion = $buildVersion
echo fullVersion = $fullVersion

# Generate the plist from the template
sed "s|JAR_NAME_STRING_GOES_HERE|$buildVersion.jar|" package/mac/Info.template.plist >package/mac/Info.plist


mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp core/target/shaded.jar core/updatefx/builds/$buildVersion.jar

java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ core/updatefx

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
    -outdir core/deploy \
    -srcfiles core/updatefx/builds/processed/$buildVersion.jar \
    -appclass io.bitsquare.app.core.BitsquareAppMain \
    -outfile Bitsquare
    
cd package/mac