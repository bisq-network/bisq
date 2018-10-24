#!/bin/sh

# not tested script based on what @devinbileck posted in https://github.com/bisq-network/bisq/issues/1791

curl -L -O https://download.java.net/java/GA/jdk10/10.0.2/19aef61b38124481863b1413dce1855f/13/openjdk-10.0.2_linux-x64_bin.tar.gz
sudo mkdir /usr/local/jvm/openjdk-10
sudo tar -zxf openjdk-10.0.2_linux-x64_bin.tar.gz -C /usr/local/jvm/openjdk-10
sudo update-alternatives --install "/usr/bin/java" "java" "/usr/local/jvm/openjdk-10/jdk-10.0.2/bin/java" 1500
sudo update-alternatives --install "/usr/bin/javac" "javac" "/usr/local/jvm/openjdk-10/jdk-10.0.2/bin/javac" 1500
git clone https://github.com/bisq-network/bisq
cd bisq
./gradlew build
java -jar desktop/build/libs/desktop-0.8.0-SNAPSHOT-all.jar
