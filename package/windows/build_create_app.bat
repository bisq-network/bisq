﻿@echo off

cd ..\..\
call mvn clean package -DskipTests -Dmaven.javadoc.skip=true
copy gui\target\shaded.jar gui\updatefx\builds\1.jar

:: edit url
call java -Xmx2048m -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
call "c:\Program Files\Java\jdk1.8.0_40\bin\javapackager.exe" -deploy -BappVersion=0.1 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.gui.BitsquareAppMain -srcfiles "gui\updatefx\builds\processed\1.jar" -outfile Bitsquare -Bruntime="c:\Program Files\Java\jdk1.8.0_40\jre"

cd package\win