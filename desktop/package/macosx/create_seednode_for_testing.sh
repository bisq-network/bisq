#!/bin/bash

cd $(dirname $0)/../../

set -e

version="1.1.5-SNAPSHOT"
commithash="ec633f0c3771893b47956b8d05b17c6f3f1919c1"

cd ..
./gradlew :seednode:build -x test shadowJar
cd seednode

mkdir -p deploy

EXE_JAR=build/libs/seednode-all.jar
JAR_WITH_HASH_NAME=seednode-$version-$commithash-all.jar
EXE_JAR_WITH_HASH=build/libs/$JAR_WITH_HASH_NAME
DEPLOY_JAR=deploy/$JAR_WITH_HASH_NAME

# we need to strip out Java 9 module configuration used in the fontawesomefx library as it causes the javapackager to stop,
# because of this existing module information, although it is not used as a module.
echo Unzipping jar to delete module config
tmp=build/libs/tmp
unzip -o -q $EXE_JAR -d $tmp

# Sometimes $tmp/module-info.class is not available. TODO check why and if still needed
rm -f $tmp/module-info.class

rm $EXE_JAR
echo Zipping jar again without module config
cd $tmp; zip -r -q -X "../$JAR_WITH_HASH_NAME" *
cd ../../../; rm -rf $tmp

cp $EXE_JAR_WITH_HASH  $DEPLOY_JAR

echo Create signature
gpg --digest-algo SHA256 --local-user $BISQ_GPG_USER --output $DEPLOY_JAR.asc --detach-sig --armor $DEPLOY_JAR

echo Verify signatures
gpg --digest-algo SHA256 --verify $DEPLOY_JAR{.asc*,}

open deploy
