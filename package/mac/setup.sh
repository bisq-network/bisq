#!/bin/bash

# setup dirs
cd ../../
mkdir gui/updatefx
mkdir gui/updatefx/builds
mkdir gui/updatefx/builds/processed
mkdir gui/updatefx/site
mkdir gui/deploy

# create key/wallet. Copy wallet key to UpdateProcess and use wallet for other OS builds
java -jar ./updatefx/updatefx-app-1.3-SNAPSHOT.jar --url=http://bitsquare.io/updateFX/v03 gui/updatefx

cd package/mac

# create icons
# iconutil -c icns package/bitsquare.iconset
