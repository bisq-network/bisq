#!/bin/bash

cd $(dirname $0)/../../../.

oldVersion=1.8.3
newVersion=1.8.4

find . -type f \( -name "finalize.sh" \
-o -name "create_app.sh" \
-o -name "build.gradle" \
-o -name "release.bat" \
-o -name "package.bat" \
-o -name "release.sh" \
-o -name "package.sh" \
-o -name "version.txt" \
-o -name "Dockerfile" \
\) -exec sed -i '' s/"$oldVersion-SNAPSHOT"/$newVersion/ {} +

find . -type f \( -name "Info.plist" \
-o -name "SeedNodeMain.java" \
-o -name "Version.java" \
-o -name "copy_dbs.sh" \
\) -exec sed -i '' s/$oldVersion/$newVersion/ {} +

