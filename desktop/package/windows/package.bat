:: Requirements:
::   - Inno Setup unicode installed (http://www.jrsoftware.org/isdl.php)
::   - OracleJDK 10 installed
::     Note: OpenJDK 10 does not have the javapackager util, so must use OracleJDK
:: Prior to running this script:
::   - Update version below
::   - Ensure JAVA_HOME below is pointing to OracleJDK 10 directory

@echo off

set version=1.1.2-SNAPSHOT
if not exist "%JAVA_HOME%\bin\javapackager.exe" (
    if not exist "%ProgramFiles%\Java\jdk-10.0.2" (
        echo Javapackager not found. Update JAVA_HOME variable to point to OracleJDK.
        exit /B 1
    )
    set JAVA_HOME=%ProgramFiles%\Java\jdk-10.0.2
)
set package_dir=%~dp0..
for /F "tokens=1,2,3 delims=.-" %%a in ("%version%") do (
   set file_version=%%a.%%b.%%c
)

cd %~dp0..\..\..

if exist "%package_dir%\desktop-%version%-all.jar" (
    set jar_dir=%package_dir%
    set jar_file=%package_dir%\desktop-%version%-all.jar
    set jar_filename=desktop-%version%-all.jar
    goto PackageJar
)

echo Building application
call gradlew.bat :desktop:clean :desktop:build -x test shadowJar
if exist "%~dp0..\..\..\desktop\build\libs\desktop-%version%-all.jar" (
    set jar_dir=%~dp0..\..\..\desktop\build\libs
    set jar_file=%~dp0..\..\..\desktop\build\libs\desktop-%version%-all.jar
    set jar_filename=desktop-%version%-all.jar
) else (
    echo No jar file available in %~dp0..\..\..\desktop\build\libs
    exit /B 2
)

if not exist "%TEMP%\7za920\7za.exe" (
    echo Downloading 7zip ^(command line version^) to %TEMP% in order to extract the jar
    powershell -Command "Invoke-WebRequest https://www.7-zip.org/a/7za920.zip -OutFile $env:temp\7za920.zip"
    powershell -Command "Expand-Archive $env:temp\7za920.zip -DestinationPath $env:temp\7za920 -Force"
)

set tmp_dir=%~dp0..\..\..\desktop\build\libs\tmp
echo Extracting jar file to %tmp_dir%
if exist "%tmp_dir%" (
    rmdir /S /Q "%tmp_dir%"
)
md "%tmp_dir%"
"%TEMP%\7za920\7za.exe" x "%jar_file%" -o"%tmp_dir%" -r -y

echo Deleting problematic module config from extracted jar
:: Strip out Java 9 module configuration used in the fontawesomefx library as it causes javapackager to stop
:: because of this existing module information, since it is not used as a module.
:: Sometimes module-info.class does not exist - TODO check why and if still needed
if exist "%tmp_dir%\module-info.class" (
    del /Q "%tmp_dir%\module-info.class"
)

echo Zipping jar again without module config
set jar_file=%package_dir%\%jar_filename%
if exist "%jar_file%" (
    del /Q "%jar_file%"
)
"%TEMP%\7za920\7za.exe" a -tzip "%jar_file%" "%tmp_dir%\*" -r
rmdir /S /Q "%tmp_dir%"

if exist "%TEMP%\7za920.zip" (
    echo Removing downloaded files
    del /Q "%TEMP%\7za920.zip"
)

echo SHA256 before stripping jar file:
for /F "delims=" %%h in ('certutil -hashfile "%jar_file%" SHA256 ^| findstr -i -v "SHA256" ^| findstr -i -v "certutil"') do (set hash=%%h)
echo %hash%

echo Making deterministic jar by stripping out parameters and comments that contain dates
:: Jar file created from https://github.com/ManfredKarrer/tools
:: TODO Is this step still necessary? Since we are using preserveFileTimestamps and reproducibleFileOrder in build.gradle
java -jar "%CD%\desktop\package\tools-1.0.jar" "%jar_file%"

echo SHA256 after stripping jar file:
for /F "delims=" %%h in ('certutil -hashfile "%jar_file%" SHA256 ^| findstr -i -v "SHA256" ^| findstr -i -v "certutil"') do (set hash=%%h)
echo %hash%
echo %hash% > "%package_dir%\%jar_filename%.txt"

:PackageJar
if exist "%package_dir%\windows\Bisq-%version%.exe" (
    del /Q "%package_dir%\windows\Bisq-%version%.exe"
)

cd desktop

echo Generating packaged executable
call "%JAVA_HOME%\bin\javapackager.exe" -deploy ^
-native exe ^
-name Bisq ^
-title Bisq ^
-vendor Bisq ^
-outdir "%package_dir%\windows" ^
-appclass bisq.desktop.app.BisqAppMain ^
-srcdir "%package_dir%" ^
-srcfiles %jar_filename% ^
-outfile Bisq ^
-v

if not exist "%package_dir%\windows\Bisq-%version%.exe" (
    echo No exe file found at %package_dir%\windows\Bisq-%version%.exe
    exit /B 3
)

echo SHA256 of %package_dir%\windows\Bisq-%version%.exe:
for /F "delims=" %%h in ('certutil -hashfile "%package_dir%\windows\Bisq-%version%.exe" SHA256 ^| findstr -i -v "SHA256" ^| findstr -i -v "certutil"') do (set hash=%%h)
echo %hash%
echo %hash% > "%package_dir%\windows\Bisq-%version%.exe.txt"

echo Done!
pause
