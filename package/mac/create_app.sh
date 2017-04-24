#!/bin/bash

cd ../../
mkdir -p gui/deploy

set -e

version="0.5.0.0"

mvn clean package verify -DskipTests -Dmaven.javadoc.skip=true

# At windows we don't add the version nr as it would keep multiple versions of jar files in app dir
cp gui/target/shaded.jar "gui/deploy/bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu/bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows/bisq.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_ubuntu14_32bit/bisq-$version.jar"
cp gui/target/shaded.jar "/Users/dev/vm_shared_windows_32bit/bisq.jar"

echo "Using JAVA_HOME: $JAVA_HOME"
$JAVA_HOME/bin/javapackager \
    -deploy \
    -BappVersion=$version \
    -Bmac.CFBundleIdentifier=io.bisq \
    -Bmac.CFBundleName=bisq \
    -Bicon=package/mac/bisq.icns \
    -Bruntime="$JAVA_HOME/jre" \
    -native dmg \
    -name bisq \
    -title bisq \
    -vendor bisq \
    -outdir gui/deploy \
    -srcfiles "gui/deploy/bisq-$version.jar" \
    -srcfiles "core/src/main/resources/bisq.policy" \
    -appclass io.bisq.gui.app.BisqAppMain \
    -outfile bisq
 
# when we have support for security manager we use that     
#     \
#    -BjvmOptions=-Djava.security.manager \
#    -BjvmOptions=-Djava.security.debug=failure \
#    -BjvmOptions=-Djava.security.policy=file:bisq.policy

rm "gui/deploy/bisq.html"
rm "gui/deploy/bisq.jnlp"

mv "gui/deploy/bundles/bisq-$version.dmg" "gui/deploy/bisq-$version.dmg"
rm -r "gui/deploy/bundles"

cd package/mac