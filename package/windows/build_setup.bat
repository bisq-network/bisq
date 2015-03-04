﻿@echo off

﻿:: setup dirs
cd ..\..\
mkdir gui\updatefx
mkdir gui\updatefx\builds
mkdir gui\updatefx\builds\processed
mkdir gui\updatefx\site
mkdir gui\deploy

:: create key/wallet. Copy wallet to UpdateProcess or use wallet form other OS build
call java -Xmx2048m -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

cd package\win

:: start webserver for update data
:: cd ..\..\gui\updatefx\site
:: python -m SimpleHTTPServer 8000