#!/bin/sh

sudo -i

JAVA_HOME=/usr/lib/jvm/java-8-oracle

echo "Install Oracle Java 8, git, maven, unzip"
apt-get update
add-apt-repository ppa:webupd8team/java
apt-get update
apt-get -y install oracle-java8-installer git maven unzip

# Alternatively you can download the latest jdk and extract it to $JAVA_HOME
# wget http://download.oracle.com/otn-pub/java/jdk/8u112-b15/jdk-8u112-linux-x64.tar.gz --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie"
# If you had an older java version installed set the new java version as default by those commands:
apt-get install update-alternatives
# update-alternatives --install /usr/bin/java java $JAVA_HOME/bin/java 2000
# update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2000
# Test with java -version and javac- version if the version is correct. Otherwise check here:
# sudo update-alternatives --config java
# sudo update-alternatives --config javac


echo "Enable unlimited Strength for cryptographic keys"
wget --no-check-certificate --no-cookies --header "Cookie: oraclelicense=accept-securebackup-cookie" http://download.oracle.com/otn-pub/java/jce/8/jce_policy-8.zip
#apt-get install unzip
unzip jce_policy-8.zip
sudo cp UnlimitedJCEPolicyJDK8/US_export_policy.jar $JAVA_HOME/jre/lib/security/US_export_policy.jar
sudo cp UnlimitedJCEPolicyJDK8/local_policy.jar $JAVA_HOME/jre/lib/security/local_policy.jar

sudo chmod 777 $JAVA_HOME/jre/lib/security/US_export_policy.jar
sudo chmod 777 $JAVA_HOME/jre/lib/security/local_policy.jar

rm -r UnlimitedJCEPolicyJDK8 jce_policy-8.zip

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

echo "Add BountyCastle.jar"
cd ~
sudo cp .m2/repository/org/bouncycastle/bcprov-jdk15on/1.53/bcprov-jdk15on-1.53.jar $JAVA_HOME/jre/lib/ext/bcprov-jdk15on-1.53.jar


