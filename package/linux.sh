#!/bin/bash

set -e

version=$1
jar=$2
mainClass=$3

$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bcategory=Finance \
    -BlicenseType=GPLv3 \
    -Bemail=info@bitsquare.io \
    -native deb \
    -name Bitsquare \
    -title Bitsquare \
    -vendor Bitsquare \
    -outdir build \
    -appclass $mainClass \
    -srcfiles $jar \
    -outfile Bitsquare

# -Bicon=client/icons/icon.png \
