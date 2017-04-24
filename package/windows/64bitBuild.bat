:: Invoke from bisq home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

SET version=0.5.0.0

:: Private setup
SET outdir=\\VBOXSVR\vm_shared_windows
:: Others might use the following
:: SET outdir=.

call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name bisq ^
-title bisq ^
-vendor bisq ^
-outdir %outdir% ^
-appclass io.bisq.gui.app.BisqAppMain ^
-srcfiles %outdir%\bisq.jar ^
-srcfiles "core/src/main/resources/bisq.policy" ^
-outfile bisq ^
-Bicon=package\windows\bisq.ico ^
-Bruntime="%JAVA_HOME%\jre"
 
:: when we have support for security manager we use that 
:: -BjvmOptions=-Djava.security.manager ^
:: -BjvmOptions=-Djava.security.debug=failure ^
:: -BjvmOptions=-Djava.security.policy=file:bisq.policy ^
