#!/bin/bash

cd ../../
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/1.jar

# edit url
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=0.1.1-SNAPSHOT \
    -Bcategory=Finance \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -Bicon=icon.pngicon.png \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir gui/deploy \
    -srcfiles gui/updatefx/builds/processed/1.jar \
    -appclass io.bitsquare.app.gui.BitsquareAppMain \
    -outfile Bitsquare

cd package/linux

# TODO: Figure out where LICENSE file goes so distros don't complain about "low quality" packages.
