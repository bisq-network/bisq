#!/usr/bin/env bash

cd $(dirname $0)

echo Update OS

sudo apt-get update
sudo apt-get upgrade
sudo apt-get dist-upgrade

if [ ! -f "$JAVA_HOME/jre/lib/security/local_policy.jar" ]
then
echo "Enable strong crypto support for Java"

wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip

checksum=f3020a3922efd6626c2fff45695d527f34a8020e938a49292561f18ad1320b59 # see https://github.com/jonathancross/jc-docs/blob/master/java-strong-crypto-test/README.md

if ! echo "$checksum jce_policy-8.zip" | sha256sum -c -;
then
    echo "Checksum failed" >&2
    exit 1
fi

unzip jce_policy-8.zip
sudo cp UnlimitedJCEPolicyJDK8/{US_export_policy.jar,local_policy.jar} $JAVA_HOME/jre/lib/security/
sudo chmod 664 $JAVA_HOME/jre/lib/security/{US_export_policy.jar,local_policy.jar}
sudo rm -rf UnlimitedJCEPolicyJDK8 jce_policy-8.zip
else
echo "Strong Crypto support for Java already available"
fi

bouncyCastleJar=bcprov-jdk15on-1.56.jar

if [ ! -f "$JAVA_HOME/jre/lib/ext/$bouncyCastleJar" ]
then
echo Configure Bouncy Castle

wget "http://central.maven.org/maven2/org/bouncycastle/bcprov-jdk15on/1.56/$bouncyCastleJar"
sudo mv $bouncyCastleJar $JAVA_HOME/jre/lib/ext/
sudo chmod 777 "$JAVA_HOME/jre/lib/ext/$bouncyCastleJar"
else
echo Bouncy Castle already configured
fi
