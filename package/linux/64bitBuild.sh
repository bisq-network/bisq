#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

# Edit version
version=0.5.0.0

jarFile="/media/sf_vm_shared_ubuntu/bisq-$version.jar"

# Note: fakeroot needs to be installed on linux
$JAVA_HOME/bin/javapackager \
    -deploy \
    -Bruntime="$JAVA_HOME/jre" \
    -BappVersion=$version \
    -Bcategory=Network \
    -Bemail=team@bitsquare.io \
    -BlicenseType=GPLv3 \
    -BlicenseFile=LICENSE \
    -Bicon=package/linux/icon.png \
    -native deb \
    -name bisq \
    -title bisq \
    -vendor bisq \
    -outdir gui/deploy \
    -srcfiles $jarFile:$jdkfixFile \
    -srcfiles "core/src/main/resources/bitsquare.policy" \
    -srcfiles package/linux/LICENSE \
    -appclass io.bitsquare.app.BitsquareAppMain \
    -outfile bisq \
    -BjvmOptions=-Djava.security.manager \
    -BjvmOptions=-Djava.security.debug=failure \
    -BjvmOptions=-Djava.security.policy=file:bitsquare.policy \


# uncomment because the build VM does not support alien
#sudo alien -r -c -k gui/deploy/bundles/bitsquare-$version.deb

cp "gui/deploy/bundles/bitsquare-$version.deb" "/home/mk/Desktop/bisq-64bit-$version.deb"
mv "gui/deploy/bundles/bitsquare-$version.deb" "/media/sf_vm_shared_ubuntu/bisq-64bit-$version.deb"
#mv "bitsquare-$version-1.x86_64.rpm" "/media/sf_vm_shared_ubuntu/bisq-64bit-$version.rpm"
rm -r gui/deploy/

cd package/linux
