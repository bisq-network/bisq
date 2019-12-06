:: Requirements:
::   - GPG installed (https://gpg4win.org/get-gpg4win.html)
::   - GPG key has been created
:: Prior to running this script:
::   - Update version below

@echo off

set version=1.2.4-SNAPSHOT
set release_dir=%~dp0..\..\..\releases\%version%
set package_dir=%~dp0..

set dmg=Bisq-%version%.dmg
set deb=Bisq-%version%.deb
set exe=Bisq-%version%.exe

set /P gpg_user="Enter email address used for gpg signing: "

echo Creating release directory
if exist "%release_dir%" (
    rmdir /S /Q "%release_dir%"
)
md "%release_dir%"

echo Copying files to release folder
:: sig key mkarrer
xcopy /Y "%~dp0..\F379A1C6.asc" "%release_dir%"
:: sig key cbeams
xcopy /Y "%~dp0..\5BC5ED73.asc" "%release_dir%"
:: sig key Christoph Atteneder
xcopy /Y "%~dp0..\29CDFD3B.asc" "%release_dir%"
:: signing key
xcopy /Y "%~dp0..\signingkey.asc" "%release_dir%"
if exist "%package_dir%\macosx\%dmg%" (
    xcopy /Y "%package_dir%\macosx\%dmg%" "%release_dir%"
    xcopy /Y "%package_dir%\macosx\%dmg%.txt" "%release_dir%"
)
if exist "%package_dir%\linux\%deb%" (
    xcopy /Y "%package_dir%\linux\%deb%" "%release_dir%"
    xcopy /Y "%package_dir%\linux\%deb%.txt" "%release_dir%"
)
if exist "%package_dir%\windows\%exe%" (
    xcopy /Y "%package_dir%\windows\%exe%" "%release_dir%"
    xcopy /Y "%package_dir%\windows\%exe%.txt" "%release_dir%"
)

echo Creating signatures
if exist "%release_dir%\%dmg%" (
    gpg --digest-algo SHA256 --local-user %gpg_user% --output "%release_dir%\%dmg%.asc" --detach-sig --armor "%release_dir%\%dmg%"
)
if exist "%release_dir%\%deb%" (
    gpg --digest-algo SHA256 --local-user %gpg_user% --output "%release_dir%\%deb%.asc" --detach-sig --armor "%release_dir%\%deb%"
)
if exist "%release_dir%\%exe%" (
    gpg --digest-algo SHA256 --local-user %gpg_user% --output "%release_dir%\%exe%.asc" --detach-sig --armor "%release_dir%\%exe%"
)

echo Verifying signatures
if exist "%release_dir%\%dmg%" (
    gpg --digest-algo SHA256 --verify "%release_dir%\%dmg%.asc"
)
if exist "%release_dir%\%deb%" (
    gpg --digest-algo SHA256 --verify "%release_dir%\%deb%.asc"
)
if exist "%release_dir%\%exe%" (
    gpg --digest-algo SHA256 --verify "%release_dir%\%exe%.asc"
)

echo Done!
pause
