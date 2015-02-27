#!/bin/bash

# edit path
cd /Users/mk/Documents/_intellij/bitsquare_UpdateFX_maven 
mvn clean package -DskipTests -Dmaven.javadoc.skip=true

# edit version /*.jar
cp gui/target/shaded.jar gui/updatefx/builds/2.jar

# edit url
java -jar ./updatefx/updatefx-app-1.2.jar --url=http://localhost:8000/ gui/updatefx