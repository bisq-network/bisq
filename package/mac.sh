#!/bin/bash

set -e

version=$1
jar=$2
mainClass=$3

javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=bitsquare \
    -Bmac.CFBundleName=Bitsquare \
    -Bruntime="$JAVA_HOME/../../" \
    -native dmg \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir build \
    -srcfiles $jar \
    -appclass $mainClass \
    -outfile Bitsquare

#-Bicon=client/icons/mac.icns \
