#!/bin/bash

cd "$(dirname "$0")/../../../." || exit 1

oldVersion=1.10.11
newVersion=1.10.12

# insert_snapshot_version.sh appends "-SNAPSHOT" to the version in these two files.
snapshotFiles=(desktop/package/macosx/finalize.sh desktop/src/main/resources/version.txt)
javaVersionFile=common/src/main/java/bisq/common/app/Version.java
versionFiles=(build-logic/packaging/src/main/kotlin/bisq/gradle/packaging/PackagingPlugin.kt
    desktop/package/macosx/Info.plist
    desktop/package/macosx/copy_dbs.sh
    "${snapshotFiles[@]}")

versionPattern='^[0-9]+\.[0-9]+\.[0-9]+$'
[[ $newVersion =~ $versionPattern ]] || { echo "newVersion $newVersion does not have the form X.Y.Z" >&2; exit 1; }
# The other files only need to hold oldVersion once Version.VERSION is exactly oldVersion.
grep -qxF "    public static final String VERSION = \"$oldVersion\";" "$javaVersionFile" ||
    { echo "Version.VERSION is not $oldVersion" >&2; exit 1; }
for file in "${versionFiles[@]}" desktop/package/macosx/insert_snapshot_version.sh reproducible-build/rpm/Dockerfile; do
    grep -qF "$oldVersion" "$file" || { echo "$file does not hold version $oldVersion" >&2; exit 1; }
done

# An attached backup suffix is the -i form that both GNU sed and the BSD sed of macOS accept.
sedInPlace() {
    local file=$1
    shift
    sed -i.bak "$@" "$file" || exit 1
    rm "$file.bak"
}

for file in "${snapshotFiles[@]}"; do
    sedInPlace "$file" s/"$oldVersion-SNAPSHOT"/$newVersion/
done

for file in "${versionFiles[@]}"; do
    sedInPlace "$file" s/$oldVersion/$newVersion/
done

# Version.java also lists the original versions of bundled historical data stores.
sedInPlace "$javaVersionFile" -E "/^    public static final String VERSION = /s/[0-9.]+/$newVersion/"

sedInPlace desktop/package/macosx/insert_snapshot_version.sh -E "s/^version=[0-9]+\.[0-9]+\.[0-9]+$/version=$newVersion/"

sedInPlace reproducible-build/rpm/Dockerfile -E "s/^ARG TAG_NAME=v[0-9]+\.[0-9]+\.[0-9]+$/ARG TAG_NAME=v$newVersion/"
grep -qxF "ARG TAG_NAME=v$newVersion" reproducible-build/rpm/Dockerfile || { echo "TAG_NAME default not updated in reproducible-build/rpm/Dockerfile" >&2; exit 1; }
