:: Invoke from bitsquare home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 32 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

SET version=0.5.0.0

:: Private setup
SET outdir=\\VBOXSVR\vm_shared_windows_32bit
:: Others might use the following
:: SET outdir=.

call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name Bitsquare ^
-title Bitsquare ^
-vendor Bitsquare ^
-outdir %outdir% ^
-appclass io.bitsquare.app.BitsquareAppMain ^
-srcfiles %outdir%\Bitsquare-%version%.jar ^
-srcfiles "core/src/main/resources/bitsquare.policy" ^
-outfile Bitsquare ^
-Bruntime="%JAVA_HOME%\jre" ^
-BjvmOptions=-Djava.security.manager ^
-BjvmOptions=-Djava.security.debug=failure ^
-BjvmOptions=-Djava.security.policy=file:bitsquare.policy ^
-Bicon=package\windows\Bitsquare.ico

