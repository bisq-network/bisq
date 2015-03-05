cd ..\..\

:: setup dirs
mkdir gui\updatefx
mkdir gui\updatefx\builds
mkdir gui\updatefx\builds\processed
mkdir gui\updatefx\site
mkdir gui\deploy
mkdir gui\win-32bit

:: Copy wallet file from main build
call java -Xmx2048m -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx

cd package\windows