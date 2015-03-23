#!/bin/bash

cd /Users/mk/Documents/_intellij/TomP2P-master_fork/TomP2P
git reset --hard
git remote add upstream https://github.com/tomp2p/TomP2P.git
git checkout master
git pull upstream master
git push origin master

git checkout published
git reset --hard master
export COMMITHASH=$(git log --oneline -1 | cut -d" " -f1)
git grep -l 5.0-Alpha | xargs perl -p -i -e "s/5.0-Alpha(..?)-SNAPSHOT/5.0-Alpha$1.$COMMITHASH-SNAPSHOT/"
git commit -am"Qualify pom version for publication"
echo $COMMITHASH

mvn clean install -DskipTests

git show bitsquare-published-91276e8:README > README
git add README
git commit -m"Add README with publication instructions"

git tag bitsquare-published-$COMMITHASH published
git push -f origin published
git push --tags

echo $COMMITHASH


