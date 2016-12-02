:: Invoke from bitsquare home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 32 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

SET version=0.4.9.8

:: Private setup
SET outdir=\\VBOXSVR\vm_shared_windows_32bit
:: Others might use the following
:: SET outdir=.

copy gui\target\shaded.jar Bitsquare-%version%.jar
call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name Bitsquare ^
-title Bitsquare ^
-vendor Bitsquare ^
-outdir %outdir% ^
-appclass io.bitsquare.app.BitsquareAppMain ^
-srcfiles %outdir%\Bitsquare-%version%.jar ^
-outfile Bitsquare ^
-Bruntime="%JAVA_HOME%\jre" ^
-BjvmProperties=-Djava.net.preferIPv4Stack=true