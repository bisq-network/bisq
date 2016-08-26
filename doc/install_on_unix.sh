#!/bin/sh

sudo -i

JAVA_HOME=/usr/lib/jvm/java-8-oracle

echo "Install Oracle Java 8, git, maven, unzip"
apt-get update
add-apt-repository ppa:webupd8team/java
apt-get update
apt-get -y install oracle-java8-installer git maven unzip


echo "Enable unlimited Strength for cryptographic keys"
wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip
unzip jce_policy-8.zip
cp UnlimitedJCEPolicyJDK8/US_export_policy.jar $JAVA_HOME/jre/lib/security/US_export_policy.jar
cp UnlimitedJCEPolicyJDK8/local_policy.jar $JAVA_HOME/jre/lib/security/local_policy.jar

chmod 777 $JAVA_HOME/jre/lib/security/US_export_policy.jar
chmod 777 $JAVA_HOME/jre/lib/security/local_policy.jar

rm -r UnlimitedJCEPolicyJDK8
rm jce_policy-8.zip

echo "Install bitcoinj"
cd ~
git clone -b FixBloomFilters https://github.com/bitsquare/bitcoinj.git
cd bitcoinj
mvn clean install -DskipTests -Dmaven.javadoc.skip=true

echo "Install and resolve dependencies for bitsquare"
cd ~
git clone https://github.com/bitsquare/bitsquare.git
cd bitsquare
mvn clean package -DskipTests -Dmaven.javadoc.skip=true

echo "Copy the jdkfix jar file"
cp bitsquare/jdkfix/target/jdkfix-0.4.9.5.jar $JAVA_HOME/jre/lib/ext/jdkfix-0.4.9.5.jar

echo "Add BountyCastle.jar"
cd ~
cp /root/.m2/repository/org/bouncycastle/bcprov-jdk15on/1.53/bcprov-jdk15on-1.53.jar $JAVA_HOME/jre/lib/ext/bcprov-jdk15on-1.53.jar


