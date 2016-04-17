cd ..\..\
mkdir gui\deploy

:: edit iss file -> AppVersion=0.4.3

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion=0.4.3 and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
call "C:\Program Files\Java\jdk1.8.0_66\bin\javapackager.exe" -deploy -BappVersion=0.4.3 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir "\\VBOXSVR\vm_shared_windows" -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "\\VBOXSVR\vm_shared_windows\Bitsquare-0.4.3.jar" -outfile Bitsquare -Bruntime="C:\Program Files\Java\jdk1.8.0_66\jre" -BjvmProperties=-Djava.net.preferIPv4Stack=true  
 
cd package\windows