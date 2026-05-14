@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%set-windows-java21-global.ps1"
set "EXIT_CODE=%ERRORLEVEL%"

echo.
if not "%EXIT_CODE%"=="0" (
  echo Failed to set global Java 21.0.6. Exit code: %EXIT_CODE%
) else (
  echo Global Java 21.0.6 setup finished.
)
echo Open a new terminal before checking java -version.
pause
exit /b %EXIT_CODE%
