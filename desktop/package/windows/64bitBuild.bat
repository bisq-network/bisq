:: Invoke from Bisq home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

cd ../../

SET version=0.9.1

:: Private setup
::SET outdir=\\VBOXSVR\vm_shared_windows
:: Others might use the following
SET outdir=.

call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name Bisq ^
-title Bisq ^
-vendor Bisq ^
-outdir %outdir% ^
-appclass bisq.desktop.app.BisqAppMain ^
-srcdir %outdir% ^
-srcfiles Bisq.jar ^
-outfile Bisq ^
-Bruntime="%JAVA_HOME%\jre" ^
-v
