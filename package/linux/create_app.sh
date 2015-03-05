#!/bin/bash

cd ../../

set -e

# Edit versions
buildVersion=1
fullVersion=0.1.1

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/$buildVersion.jar

java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$fullVersion \
    -Bcategory=Finance \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
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
# -BlicenseFile=LICENSE comlains about missing file (Bundler DEB Installer skipped because of a configuration problem: Specified license file is missing.  
# Advice to fix: Make sure that references a file in the app resources, and that it is relative to the 
# basedir.)
