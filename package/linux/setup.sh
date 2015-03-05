#!/bin/bash

# setup dirs
cd ../../
mkdir gui/updatefx
mkdir gui/updatefx/builds
mkdir gui/updatefx/builds/processed
mkdir gui/updatefx/site
mkdir gui/deploy

# Copy wallet file from main build
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

cd package/linux