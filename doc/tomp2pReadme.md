How to publish custom TomP2P snapshots:

Update this fork to upstream/master:

    git clone https://github.com/bitsquare/TomP2P.git
    cd TomP2P
    git remote add upstream https://github.com/tomp2p/TomP2P.git
    git checkout master
    git pull upstream master
    git push origin master

Create a custom qualified snapshot version:

    git checkout published  (if the first time: git checkout -b published)
    git reset --hard master
    export COMMITHASH=$(git log --oneline -1 | cut -d" " -f1)
    git grep -l 5.0-Alpha | xargs perl -p -i -e "s/5.0-Alpha(..?)-SNAPSHOT/5.0-Alpha$1.$COMMITHASH-SNAPSHOT/"
    git diff # review changes to poms
    git commit -am"Qualify pom version for publication"

Build artifacts:

    mvn clean package -DskipTests

Upload artifacts to Artifactory:

    Log in at https://partnerdemo.artifactoryonline.com/partnerdemo/webapp/login.html (@ManfredKarrer and @cbeams know the creds)
    Go to https://partnerdemo.artifactoryonline.com/partnerdemo/webapp/deployartifact.html
    Upload each of the tomp2p jar files, and accept all defaults in the form that follows
    Finally, upload the top-level pom.xml (this is the tomp2p-parent pom).

Re-apply this README:

    git show bitsquare-published-91276e8:README > README
    git add README
    git commit -m"Add README with publication instructions"

Tag the published branch:

    git tag bitsquare-published-$COMMITHASH published

(Force) push the published branch:

    git push -f origin published

Push tags:

    git push --tags


Note this would all be a lot easier if TomP2P published its own snapshots :)

