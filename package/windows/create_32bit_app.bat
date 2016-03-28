cd ..\..\
mkdir gui\deploy

:: edit iss file -> AppVersion=0.3.6

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion=0.3.6 and -srcfiles

:: 32 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
call "C:\Program Files (x86)\Java\jdk1.8.0_77\bin\javapackager.exe" -deploy -BappVersion=0.3.6 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir "\\VBOXSVR\vm_shared_windows_32bit" -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "\\VBOXSVR\vm_shared_windows_32bit\Bitsquare-0.3.6.jar" -outfile Bitsquare -Bruntime="C:\Program Files (x86)\Java\jdk1.8.0_77\jre" -BjvmProperties=-Djava.net.preferIPv4Stack=true  

cd package\windows