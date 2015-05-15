#!/bin/bash

cd ../../

set -e

# Edit versions
fullVersion=0.2.1

# Copy jar file from mac build (1.jar from processed folder) to linux box 
# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$fullVersion \
    -Bcategory=Finance \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles gui/updatefx/builds/processed/1.jar \
    -srcfiles package/linux/LICENSE \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare

cd package/linux