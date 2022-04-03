#! /bin/bash

VERSION="$1"
if [[ -z "$VERSION" ]]; then
   VERSION="SNAPSHOT"
fi

export BISQ_RELEASE_NAME="bisq-daemon-$VERSION"
export BISQ_RELEASE_ZIP_NAME="$BISQ_RELEASE_NAME.zip"

export GRADLE_DIST_NAME="daemon.tar"
export GRADLE_DIST_PATH="../build/distributions/$GRADLE_DIST_NAME"

arrangegradledist() {
    # Arrange $BISQ_RELEASE_NAME directory structure to contain a runnable
    # jar at the top-level, and a lib dir containing dependencies:
    # .
    # |
    # |__ daemon.jar
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
	mv -v daemon/lib .
	# Rearrange $BISQ_RELEASE_NAME contents:  remove the daemon/bin and daemon directories.
	rm -rf daemon
	# Rearrange $BISQ_RELEASE_NAME contents:  move the lib/daemon.jar up one level.
	mv -v lib/daemon.jar .
}

writemanifest() {
    # Make the daemon.jar runnable, and define its dependencies in a MANIFEST.MF update.
	echo "Main-Class: bisq.daemon.app.BisqDaemonMain" > manifest-update.txt
	printf "Class-Path:  " >> manifest-update.txt
	for file in lib/*
	do
	  # Each new line in the classpath must be preceded by two spaces.
	  printf "  %s\n" "$file" >> manifest-update.txt
	done
}

updatemanifest() {
    # Append contents of to daemon.jar's MANIFEST.MF.
	jar uvfm daemon.jar manifest-update.txt
}

ziprelease() {
	cd ..
	zip -r $BISQ_RELEASE_ZIP_NAME $BISQ_RELEASE_NAME/lib $BISQ_RELEASE_NAME/daemon.jar
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

