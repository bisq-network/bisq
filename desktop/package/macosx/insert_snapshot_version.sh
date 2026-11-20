#!/bin/bash

cd $(dirname $0)/../../../

version=1.10.5

# replace_version_number.sh turns "$version-SNAPSHOT" into the next version in these two files only.
find . -type f \( -name "finalize.sh" \
-o -name "version.txt" \
\) -exec sed -i s/$version/"$version-SNAPSHOT"/ {} +
