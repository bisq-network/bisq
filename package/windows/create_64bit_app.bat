﻿cd ..\..\
mkdir gui\deploy

:: edit iss file -> AppVersion

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

SET version=0.4.9
SET jdk=C:\Program Files\Java\jdk1.8.0_92
SET outdir=\\VBOXSVR\vm_shared_windows

call "%jdk%\bin\javapackager.exe" -deploy ^
-BjvmOptions=-Xbootclasspath/a:^"jdkfix-%version%.jar^";^"..\runtime\lib\ext\jfxrt.jar^" ^
-BappVersion=%version% ^
-native exe ^
-name Bitsquare ^
-title Bitsquare ^
-vendor Bitsquare ^
-outdir %outdir% ^
-appclass io.bitsquare.app.BitsquareAppMain ^
-srcfiles "%outdir%\Bitsquare-%version%.jar;%outdir%\jdkfix-%version%.jar" ^
-outfile Bitsquare ^
-Bruntime="%jdk%\jre" ^
-BjvmProperties=-Djava.net.preferIPv4Stack=true

:: -BjvmOptions=-verbose:class
:: that works if used form the terminal:
:: java -Xbootclasspath/a:"jdkfix-0.4.9.jar";"C:\Users\asd\AppData\Local\Bitsquare\runtime\lib\ext\jfxrt.jar" -jar Bitsquare-0.4.9.jar

cd package\windows
