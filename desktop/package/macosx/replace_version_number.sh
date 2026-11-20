#!/bin/bash

cd $(dirname $0)/../../../.

oldVersion=1.10.11
newVersion=1.10.12

find . -type f \( -name "finalize.sh" \
-o -name "version.txt" \
\) -exec sed -i s/"$oldVersion-SNAPSHOT"/$newVersion/ {} +

find . -type f \( -name "Info.plist" \
-o -name "PackagingPlugin.kt" \
-o -name "Version.java" \
-o -name "copy_dbs.sh" \
-o -name "finalize.sh" \
-o -name "version.txt" \
\) -exec sed -i s/$oldVersion/$newVersion/ {} +

sed -i -E "s/^version=[0-9]+\.[0-9]+\.[0-9]+$/version=$newVersion/" desktop/package/macosx/insert_snapshot_version.sh

sed -i -E "s/^ARG TAG_NAME=v[0-9]+\.[0-9]+\.[0-9]+$/ARG TAG_NAME=v$newVersion/" reproducible-build/rpm/Dockerfile
grep -qxF "ARG TAG_NAME=v$newVersion" reproducible-build/rpm/Dockerfile || { echo "TAG_NAME default not updated in reproducible-build/rpm/Dockerfile" >&2; exit 1; }

