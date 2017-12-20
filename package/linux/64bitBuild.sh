#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit version
version=0.6.2

dir="/media/sf_vm_shared_ubuntu"

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


# uncomment because the build VM does not support alien
#sudo alien -r -c -k gui/deploy/bundles/bisq-$version.deb

cp "gui/deploy/bundles/bisq-$version.deb" "/home/mk/Desktop/Bisq-64bit-$version.deb"
mv "gui/deploy/bundles/bisq-$version.deb" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.deb"
#mv "bisq-$version-1.x86_64.rpm" "/media/sf_vm_shared_ubuntu/Bisq-64bit-$version.rpm"
rm -r gui/deploy/

cd package/linux
