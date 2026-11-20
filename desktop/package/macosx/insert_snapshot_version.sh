#!/bin/bash

cd "$(dirname "$0")/../../../" || exit 1

version=1.10.12

# replace_version_number.sh turns "$version-SNAPSHOT" into the next version in these two files only.
find . -type f \( -name "finalize.sh" \
-o -name "version.txt" \
\) -exec sed -i s/$version/"$version-SNAPSHOT"/ {} +
