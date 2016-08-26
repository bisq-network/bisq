;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Bitsquare with application files
[Setup]
AppId={{bitsquare}}
AppName=Bitsquare
AppVersion=0.4.9.5
AppVerName=Bitsquare
AppPublisher=Bitsquare
AppComments=Bitsquare
AppCopyright=Copyright (C) 2016
AppPublisherURL=https://bitsquare.io
AppSupportURL=https://bitsquare.io
;AppUpdatesURL=http://java.com/
DefaultDirName={localappdata}\Bitsquare
DisableStartupPrompt=Yes
DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=Bitsquare
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1
OutputBaseFilename=Bitsquare
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=Bitsquare\Bitsquare.ico
UninstallDisplayIcon={app}\Bitsquare.ico
UninstallDisplayName=Bitsquare
WizardImageStretch=No
WizardSmallImageFile=Bitsquare-setup-icon.bmp
ArchitecturesInstallIn64BitMode=x64
ChangesAssociations=Yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "Bitsquare\Bitsquare.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "Bitsquare\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Bitsquare"; Filename: "{app}\Bitsquare.exe"; IconFilename: "{app}\Bitsquare.ico"; Check: returnTrue()
Name: "{commondesktop}\Bitsquare"; Filename: "{app}\Bitsquare.exe";  IconFilename: "{app}\Bitsquare.ico"; Check: returnFalse()

[Run]
Filename: "{app}\Bitsquare.exe"; Description: "{cm:LaunchProgram,Bitsquare}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\Bitsquare.exe"; Parameters: "-install -svcName ""Bitsquare"" -svcDesc ""Bitsquare"" -mainExe ""Bitsquare.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\Bitsquare.exe "; Parameters: "-uninstall -svcName Bitsquare -stopOnUninstall"; Check: returnFalse()

[Code]
function returnTrue(): Boolean;
begin
  Result := True;
end;

function returnFalse(): Boolean;
begin
  Result := False;
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support?
  Result := True;
end;
