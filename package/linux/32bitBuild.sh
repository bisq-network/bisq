#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit version
version=0.5.0.0

jarFile="/media/sf_vm_shared_ubuntu14_32bit/bisq-$version.jar"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=team@bisq.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name bisq \
    -title bisq \
    -vendor bisq \
    -outdir gui/deploy \
    -srcfiles $jarFile:$jdkfixFile \
    -srcfiles "core/src/main/resources/bisq.policy" \
    -srcfiles package/linux/LICENSE \
    -appclass io.bisq.gui.app.BisqAppMain \
    -outfile bisq
     
# when we have support for security manager we use that     
#     \
#    -BjvmOptions=-Djava.security.manager \
#    -BjvmOptions=-Djava.security.debug=failure \
#    -BjvmOptions=-Djava.security.policy=file:bisq.policy


# sudo alien -r -c -k gui/deploy/bundles/bisq-$version.deb

cp "gui/deploy/bundles/bisq-$version.deb" "﻿/home/bisq/Desktop/bisq-32bit-$version.deb"
mv "gui/deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu14_32bit/bisq-32bit-$version.deb"
# mv "bisq-$version-1.i386.rpm" "/media/sf_vm_shared_ubuntu14_32bit/bisq-32bit-$version.rpm"
rm -r gui/deploy/

cd package/linux
