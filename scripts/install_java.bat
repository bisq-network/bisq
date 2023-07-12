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

set jdk_version=11.0.2
set jdk_filename=openjdk-%jdk_version%_windows-x64_bin
set jdk_url=https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_windows-x64_bin.zip

if exist "%PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%" (
    echo %PROGRAMFILES%\Java\openjdk\jdk-%jdk_version% already exists, skipping install
    goto SetEnvVars
)

echo Downloading required files to %TEMP%
powershell -Command "Invoke-WebRequest %jdk_url% -OutFile $env:temp\%jdk_filename%.zip"

echo Extracting and installing JDK to %PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%
powershell -Command "Expand-Archive $env:temp\%jdk_filename%.zip -DestinationPath %TEMP%\openjdk-%jdk_version% -Force"
md "%PROGRAMFILES%\Java\openjdk"
move "%TEMP%\openjdk-%jdk_version%\jdk-%jdk_version%" "%PROGRAMFILES%\Java\openjdk"

echo Removing downloaded files
rmdir /S /Q %TEMP%\openjdk-%jdk_version%
del /Q %TEMP%\%jdk_filename%.zip

:SetEnvVars
echo Setting environment variables
powershell -Command "[Environment]::SetEnvironmentVariable('JAVA_HOME', '%PROGRAMFILES%\Java\openjdk\jdk-%jdk_version%', 'Machine')"
set java_bin=%%JAVA_HOME%%\bin
echo %PATH%|find /i "%java_bin%">nul || powershell -Command "[Environment]::SetEnvironmentVariable('PATH', '%PATH%;%java_bin%', 'Machine')"

echo Done!
pause
