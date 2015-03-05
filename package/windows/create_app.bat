cd ..\..\
::call mvn clean package -DskipTests -Dmaven.javadoc.skip=true
::copy gui\target\shaded.jar gui\updatefx\builds\1.jar

:: edit url
::call java -Xmx2048m -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

:: 64 bit build
:: Needs Inno Setup 5 or later (http://www.jrsoftware.org/isdl.php)
:: Build with jdk1.8.0_40 fails but jdk1.8.0_3 works
call "C:\Program Files\Java\jdk1.8.0_31\bin\javapackager.exe" -deploy -BappVersion=0.1 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy -appclass io.bitsquare.app.gui.BitsquareAppMain -srcfiles "gui\updatefx\builds\processed\1.jar" -outfile Bitsquare -Bruntime="C:\Program Files\Java\jdk1.8.0_31\jre"

:: 32 bit build
::call "C:\Program Files (x86)\Java\jdk1.8.0_31\bin\javapackager.exe" -deploy -BappVersion=0.1 -native exe -name Bitsquare -title Bitsquare -vendor Bitsquare -outdir gui\deploy\win-32bit -appclass io.bitsquare.app.gui.BitsquareAppMain -srcfiles "gui\updatefx\builds\processed\1.jar" -outfile Bitsquare -Bruntime="C:\Program Files (x86)\Java\jdk1.8.0_31\jre"

cd package\windows