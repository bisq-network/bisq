#!/usr/bin/env bash

mkdir -p $HOME/.binaries
cd $HOME/.binaries

maven_version=3.5.0

if [ '!' -d apache-maven-${maven_version} ]
then
  if [ '!' -f apache-maven-${maven_version}-bin.zip ]
  then
    wget https://archive.apache.org/dist/maven/maven-3/${maven_version}/binaries/apache-maven-${maven_version}-bin.zip || exit 1
  fi
  echo "Installing maven ${maven_version}"
  unzip -qq apache-maven-${maven_version}-bin.zip || exit 1
  rm -f apache-maven-${maven_version}-bin.zip
fi

exit 0