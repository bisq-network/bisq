#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit version
version=0.6.2

dir="/media/sf_vm_shared_ubuntu14_32bit"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=contact@bisq.network \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name Bisq \
    -title Bisq \
    -vendor Bisq \
    -outdir gui/deploy \
    -srcfiles "$dir/Bisq-$version.jar" \
    -srcfiles "$dir/bcpg-jdk15on.jar" \
    -srcfiles "$dir/bcprov-jdk15on.jar" \
    -srcfiles package/linux/LICENSE \
    -appclass io.bisq.gui.app.BisqAppMain \
    -BjvmOptions=-Xss1280k \
    -outfile Bisq

# when we have support for security manager we use that
#     \
#    -BjvmOptions=-Djava.security.manager \
#    -BjvmOptions=-Djava.security.debug=failure \
#    -BjvmOptions=-Djava.security.policy=file:bisq.policy
#     -srcfiles "core/src/main/resources/bisq.policy" \



# sudo alien -r -c -k gui/deploy/bundles/bisq-$version.deb

cp "gui/deploy/bundles/bisq-$version.deb" "/home/bitsquare/Desktop/Bisq-32bit-$version.deb"
mv "gui/deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu14_32bit/Bisq-32bit-$version.deb"

# mv "bisq-$version-1.i386.rpm" "/media/sf_vm_shared_ubuntu14_32bit/Bisq-32bit-$version.rpm"
rm -r gui/deploy/

cd package/linux
