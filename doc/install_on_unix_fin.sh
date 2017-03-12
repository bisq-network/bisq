#!/bin/sh

sudo -i

JAVA_HOME=/usr/lib/jvm/java-8-oracle

# Before running the second part edit $JAVA_HOME/jre/lib/security/java.security file
# add line: security.provider.10=org.bouncycastle.jce.provider.BouncyCastleProvider

# and add JAVA_HOME to .bashrc
# export JAVA_HOME=/usr/lib/jvm/java-8-oracle

echo "Install bisq"
cd ~/bisq
mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cd ..
mkdir .local
mkdir .local/share

echo "Start bisq"
java -jar ~/bisq/gui/target/shaded.jar


