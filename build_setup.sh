#!/bin/bash

# setup dirs
cd /Users/admin_mbp/Dropbox/Bitsquare2
mkdir gui/updatefx
mkdir gui/updatefx/builds
mkdir gui/updatefx/builds/processed
mkdir gui/updatefx/site
mkdir gui/deploy

# create key/wallet
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

# start webserver for update data
cd /Users/admin_mbp/Dropbox/Bitsquare2/gui/updatefx/site
# python -m SimpleHTTPServer 8000

# create icons
# iconutil -c icns package/bitsquare.iconset
