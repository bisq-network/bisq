#!/bin/bash

# edit path
cd /Users/mk/Documents/_intellij/bitsquare_UpdateFX_maven 

mvn clean package -DskipTests -Dmaven.javadoc.skip=true
cp gui/target/shaded.jar gui/updatefx/builds/1.jar

# edit url
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx

# edit JAVA_HOME and different OS binaries
/Library/Java/JavaVirtualMachines/jdk1.8.0_20.jdk/Contents/Home/bin/javapackager -deploy -outdir gui/deploy/ -outfile Bitsquare.dmg -name Bitsquare -native dmg -appclass io.bitsquare.app.gui.BitsquareAppMain -srcfiles gui/updatefx/builds/processed/1.jar
