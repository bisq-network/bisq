#!/bin/sh

# TODO that script is outdated. See https://github.com/bisq-network/bisq/blob/master/README.md for new setup with OpenJdk10.


sudo -i

JAVA_HOME=/usr/lib/jvm/java-8-oracle

# Before running the second part edit $JAVA_HOME/jre/lib/security/java.security file
# add line: security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider

# and add JAVA_HOME to .bashrc
# export JAVA_HOME=/usr/lib/jvm/java-8-oracle

echo "Install Bisq"
cd ~/bisq
./gradlew build
cd ..
mkdir .local
mkdir .local/share

echo "Start Bisq"
./build/app/bin/bisq-desktop
