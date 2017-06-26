#!/bin/bash

cd /Users/dev/Documents/_intellij/bitcoinj-fork
git reset --hard
git remote add upstream https://github.com/bitcoinj/bitcoinj.git
git checkout master
git pull upstream master
git push origin master

git checkout published
git reset --hard master
export COMMITHASH=$(git log --oneline -1 | cut -d" " -f1)
echo $COMMITHASH
git grep -l 0.13-SNAPSHOT | xargs perl -p -i -e "s/0.13-SNAPSHOT/0.13.$COMMITHASH-SNAPSHOT/"
git commit -am"Qualify pom version for publication"

mvn clean install -DskipTests

git tag bisq-published-$COMMITHASH published
git push -f origin published
git push --tags


