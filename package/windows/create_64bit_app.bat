cd ..\..\
mkdir gui\deploy

:: edit iss file -> AppVersion

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
call "C:\Program Files\Java\jdk1.8.0_92\bin\javapackager.exe" -deploy -BappVersion=0.4.7 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir "\\VBOXSVR\vm_shared_windows" -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "\\VBOXSVR\vm_shared_windows\Bitsquare-0.4.7.jar" -outfile Bitsquare -Bruntime="C:\Program Files\Java\jdk1.8.0_92\jre" -BjvmProperties=-Djava.net.preferIPv4Stack=true  
 
cd package\windows