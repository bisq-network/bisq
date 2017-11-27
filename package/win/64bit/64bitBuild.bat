:: Invoke from bisq home directory
:: edit iss file -> AppVersion
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)

SET version=0.6.1

:: Private setup
SET outdir=\\VBOXSVR\vm_shared_windows
:: Others might use the following
:: SET outdir=.

call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-BappVersion="%version%" ^
-native exe ^
-name Bisq ^
-title Bisq ^
-vendor Bisq ^
-outdir %outdir% ^
-appclass io.bisq.gui.app.BisqAppMain ^
-srcfiles %outdir%\Bisq.jar ^
-outfile Bisq ^
-Bruntime="%JAVA_HOME%\jre"

:: when we have support for security manager we use that
:: -BjvmOptions=-Djava.security.manager ^
:: -BjvmOptions=-Djava.security.debug=failure ^
:: -BjvmOptions=-Djava.security.policy=file:bisq.policy ^

:: -srcfiles "core/src/main/resources/bisq.policy" ^
