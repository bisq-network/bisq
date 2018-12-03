#!/bin/bash

JAVA_HOME=/usr/lib/jvm/openjdk-10.0.2

if [ ! -d "$JAVA_HOME" ]; then
    yum install curl

    curl -L -O https://download.java.net/java/GA/jdk10/10.0.2/19aef61b38124481863b1413dce1855f/13/openjdk-10.0.2_linux-x64_bin.tar.gz
    mkdir -p $JAVA_HOME
    tar -zxf openjdk-10.0.2_linux-x64_bin.tar.gz -C $JAVA_HOME --strip 1
    rm openjdk-10.0.2_linux-x64_bin.tar.gz

    update-alternatives --install /usr/bin/java java $JAVA_HOME/bin/java 2000
    update-alternatives --install /usr/bin/javac javac $JAVA_HOME/bin/javac 2000
fi
