#! /bin/bash

export BISQ_RELEASE_NAME="bisq-cli-release"
export BISQ_RELEASE_ZIP_NAME="$BISQ_RELEASE_NAME.zip"

export GRADLE_DIST_NAME="cli.tar"
export GRADLE_DIST_PATH="../build/distributions/$GRADLE_DIST_NAME"

arrangegradledist() {
	cp -v $GRADLE_DIST_PATH .
	rm -rf $BISQ_RELEASE_NAME
	mkdir $BISQ_RELEASE_NAME
	tar -xf $GRADLE_DIST_NAME -C bisq-cli-release
	cd $BISQ_RELEASE_NAME
	mv -v cli/lib .
	rm -rf cli
	mv -v lib/cli.jar .
}

writemanifest() {
	echo "Main-Class: bisq.cli.CliMain" > manifest-update.txt
	printf "Class-Path:  " >> manifest-update.txt
	for file in lib/*
	do
	  printf "  %s\n" "$file" >> manifest-update.txt
	done
}

updatemanifest() {
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

