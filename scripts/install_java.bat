:: This script will download and install the appropriate JDK for use with Bisq development.
:: It will also configure it as the default system JDK.
:: If you need to change to another default JDK for another purpose later, you just need to
:: change the JAVA_HOME environment variable. For example, use the following command:
::     setx /M JAVA_HOME "<JDK_PATH>"

@echo off

:: Ensure we have administrative privileges in order to install files and set environment variables
>nul 2>&1 "%SYSTEMROOT%\system32\cacls.exe" "%SYSTEMROOT%\system32\config\system"
if '%errorlevel%' == '0' (
    ::If no error is encountered, we have administrative privileges
    goto GotAdminPrivileges
)
echo Requesting administrative privileges...
echo Set UAC = CreateObject^("Shell.Application"^) > "%temp%\getadminprivileges.vbs"
set params = %*:"=""
echo UAC.ShellExecute "%~s0", "%params%", "", "runas", 1 >> "%temp%\getadminprivileges.vbs"
"%temp%\getadminprivileges.vbs"
exit /B
:GotAdminPrivileges
if exist "%temp%\getadminprivileges.vbs" ( del "%temp%\getadminprivileges.vbs" )
pushd "%CD%"
cd /D "%~dp0"

title Install Java

set jdk_version=10.0.2
set jdk_filename=openjdk-%jdk_version%_windows-x64_bin
set jdk_url=https://download.java.net/java/GA/jdk10/%jdk_version%/19aef61b38124481863b1413dce1855f/13/%jdk_filename%.tar.gz

if exist "%PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%" (
    echo %PROGRAMFILES%\Java\openjdk\jdk-%jdk_version% already exists, skipping install
    goto SetEnvVars
)

echo Downloading required files to %TEMP%
powershell -Command "Invoke-WebRequest %jdk_url% -OutFile $env:temp\%jdk_filename%.tar.gz"
if not exist "%TEMP%\7za920\7za.exe" (
    :: Download 7zip ^(command line version^) in order to extract the tar.gz file since there is no native support in Windows
    powershell -Command "Invoke-WebRequest https://www.7-zip.org/a/7za920.zip -OutFile $env:temp\7za920.zip"
    powershell -Command "Expand-Archive $env:temp\7za920.zip -DestinationPath $env:temp\7za920 -Force"
)

echo Extracting and installing JDK to %PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%
"%TEMP%\7za920\7za.exe" x "%TEMP%\%jdk_filename%.tar.gz" -o"%TEMP%" -r -y
"%TEMP%\7za920\7za.exe" x "%TEMP%\%jdk_filename%.tar" -o"%TEMP%\openjdk-%jdk_version%" -r -y
md "%PROGRAMFILES%\Java\openjdk"
move "%TEMP%\openjdk-%jdk_version%\jdk-%jdk_version%" "%PROGRAMFILES%\Java\openjdk"

echo Removing downloaded files
if exist "%TEMP%\7za920.zip" (
    del /Q %TEMP%\7za920.zip
)
rmdir /S /Q %TEMP%\openjdk-%jdk_version%
del /Q %TEMP%\%jdk_filename%.tar
del /Q %TEMP%\%jdk_filename%.tar.gz

:SetEnvVars
echo Setting environment variables
powershell -Command "[Environment]::SetEnvironmentVariable('JAVA_HOME', '%PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%', 'Machine')"
set java_bin=%%JAVA_HOME%%\bin
echo %PATH%|find /i "%java_bin%">nul || powershell -Command "[Environment]::SetEnvironmentVariable('PATH', '%PATH%;%java_bin%', 'Machine')"

echo Done!
pause
