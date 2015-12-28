cd ..\..\
mkdir gui\deploy

mvn clean package -DskipTests -Dmaven.javadoc.skip=true

:: edit iss file -> AppVersion=0.3.2.2

:: Copy gui/deploy.Bitsquare.jar file from mac build to windows
:: edit -> -BappVersion=0.3.2.2 and -srcfiles

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
:: Build with jdk1.8.0_25 fails but jdk1.8.0_3 works
call "C:\Program Files\Java\jdk1.8.0_25\bin\javapackager.exe" -deploy -BappVersion=0.3.2.2 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "gui\target\shaded.jar" -outfile Bitsquare -Bruntime="C:\Program Files\Java\jdk1.8.0_25\jre"

:: 32 bit build
:: call "C:\Program Files (x86)\Java\jdk1.8.0_25\bin\javapackager.exe" -deploy -BappVersion=0.3.2.2 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.BitsquareAppMain -srcfiles "gui\target\shaded.jar" -outfile Bitsquare -Bruntime="C:\Program Files (x86)\Java\jdk1.8.0_25\jre"

cd package\windows