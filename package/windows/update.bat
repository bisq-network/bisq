call mvn clean package -DskipTests -Dmaven.javadoc.skip=true

:: edit buildVersion number
@echo off set buildVersion=2

:: edit version /*.jar
cp gui\target\shaded.jar gui\updatefx\builds\%buildVersion%.jar

call java -jar ./updatefx/updatefx-app-1.2.jar --url=http://bitsquare.io/updateFX/ gui/updatefx