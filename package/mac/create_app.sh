#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

fullVersion="0.3.2.1"

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/deploy/Bitsquare.jar

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
    -srcfiles gui/deploy/Bitsquare.jar \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile Bitsquare \
    -BjvmProperties=-Djava.net.preferIPv4Stack=true
    
cp gui/deploy/Bitsquare.jar /Users/mk/vm_shared_ubuntu/Bitsquare.jar
cp gui/deploy/Bitsquare.jar /Users/mk/vm_shared_windows/Bitsquare.jar

cd package/mac