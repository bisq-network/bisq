#!/bin/bash

# setup dirs
cd ../../
mkdir core/updatefx
mkdir core/updatefx/builds
mkdir core/updatefx/builds/processed
mkdir core/updatefx/site
mkdir core/deploy

# create key/wallet. Copy wallet key to UpdateProcess and use wallet for other OS builds
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ core/updatefx

cd package/mac

# create icons
# iconutil -c icns package/bitsquare.iconset
