:: edit iss file -> AppVersion

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

:: Did not get -BjvmOptions=-Xbootclasspath working on windows, but if the jdkfix jar is copied into the jdk/jre dir it will override the default classes

SET version=0.4.9.5
SET jdk=C:\Program Files\Java\jdk1.8.0_92
SET outdir=\\VBOXSVR\vm_shared_windows

call "%jdk%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name Bitsquare ^
-title Bitsquare ^
-vendor Bitsquare ^
-outdir %outdir% ^
-appclass io.bitsquare.app.BitsquareAppMain ^
-srcfiles %outdir%\Bitsquare-%version%.jar ^
-outfile Bitsquare ^
-Bruntime="%jdk%\jre" ^
-BjvmProperties=-Djava.net.preferIPv4Stack=true