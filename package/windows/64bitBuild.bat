:: Invoke from bitsquare home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

:: Did not get -BjvmOptions=-Xbootclasspath working on windows, but if the jdkfix jar is copied into the jdk/jre dir it will override the default classes

SET version=0.4.9.6
SET outdir=.
:: \\VBOXSVR\vm_shared_windows

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