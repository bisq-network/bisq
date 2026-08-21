#! /bin/bash

VERSION="$1"
if [[ -z "$VERSION" ]]; then
   VERSION="SNAPSHOT"
fi

export BISQ_RELEASE_NAME="bisq-cli-$VERSION"
export BISQ_RELEASE_ZIP_NAME="$BISQ_RELEASE_NAME.zip"

export GRADLE_DIST_NAME="cli.tar"
export GRADLE_DIST_PATH="../build/distributions/$GRADLE_DIST_NAME"

arrangegradledist() {
    # Arrange $BISQ_RELEASE_NAME directory structure to contain a runnable
    # jar at the top-level, and a lib dir containing dependencies:
    # .
    # |
    # |__ cli.jar
    # |__ lib
    # |__ |__ dep1.jar
    # |__ |__ dep2.jar
    # |__ |__ ...
    # Copy the build's distribution tarball to this directory.
	cp -v $GRADLE_DIST_PATH .
	# Create a clean directory to hold the tarball's content.
	rm -rf $BISQ_RELEASE_NAME
	mkdir $BISQ_RELEASE_NAME
	# Extract the tarball's content into $BISQ_RELEASE_NAME.
	tar -xf $GRADLE_DIST_NAME -C $BISQ_RELEASE_NAME
	cd $BISQ_RELEASE_NAME
	# Rearrange $BISQ_RELEASE_NAME contents:  move the lib directory up one level.
	mv -v cli/lib .
	# Rearrange $BISQ_RELEASE_NAME contents:  remove the cli/bin and cli directories.
	rm -rf cli
	# Rearrange $BISQ_RELEASE_NAME contents:  move the lib/cli.jar up one level.
	mv -v lib/cli.jar .
}

writemanifest() {
    # Make the cli.jar runnable, and define its dependencies in a MANIFEST.MF update.
	echo "Main-Class: bisq.cli.CliMain" > manifest-update.txt
	printf "Class-Path:  " >> manifest-update.txt
	for file in lib/*
	do
	  # Each new line in the classpath must be preceded by two spaces.
	  printf "  %s\n" "$file" >> manifest-update.txt
	done
}

updatemanifest() {
    # Append contents of to cli.jar's MANIFEST.MF.
	jar uvfm cli.jar manifest-update.txt
}

ziprelease() {
	cd ..
	zip -r $BISQ_RELEASE_ZIP_NAME $BISQ_RELEASE_NAME/lib $BISQ_RELEASE_NAME/cli.jar
}

cleanup() {
    rm -v ./$GRADLE_DIST_NAME
    rm -r ./$BISQ_RELEASE_NAME
}

arrangegradledist
writemanifest
updatemanifest
ziprelease
cleanup

