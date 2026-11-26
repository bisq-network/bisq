#!/bin/bash

cd "$(dirname "$0")/../../../" || exit 1

version=1.10.13

# replace_version_number.sh turns "$version-SNAPSHOT" into the next version in these two files only.
finalizeScript=desktop/package/macosx/finalize.sh
versionFile=desktop/src/main/resources/version.txt

# Both files must hold exactly this version, with or without the suffix of an earlier run.
grep -qxF -e "version=\"$version\"" -e "version=\"$version-SNAPSHOT\"" "$finalizeScript" ||
    { echo "$finalizeScript does not hold version $version" >&2; exit 1; }
grep -qxF -e "$version" -e "$version-SNAPSHOT" "$versionFile" ||
    { echo "$versionFile does not hold version $version" >&2; exit 1; }

# An attached backup suffix is the -i form that both GNU sed and the BSD sed of macOS accept.
for file in "$finalizeScript" "$versionFile"; do
    sed -i.bak -e s/"$version-SNAPSHOT"/$version/ -e s/$version/"$version-SNAPSHOT"/ "$file" || exit 1
    rm "$file.bak"
done
