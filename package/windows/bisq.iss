;This file will be executed next to the application bundle image
;I.e. current directory will contain folder bisq with application files
[Setup]
AppId={{bisq}}
AppName=bisq
AppVersion=0.5.0
AppVerName=bisq
AppPublisher=bisq
AppComments=bisq
AppCopyright=Copyright (C) 2016
AppPublisherURL=https://bisq.io
AppSupportURL=https://bisq.io
;AppUpdatesURL=http://java.com/
DefaultDirName={localappdata}\bisq
DisableStartupPrompt=Yes
DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=bisq
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1
OutputBaseFilename=bisq
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile={app}\bisq.ico
UninstallDisplayIcon={app}\bisq.ico
UninstallDisplayName=bisq
WizardImageStretch=No
WizardSmallImageFile=bisq-setup-icon.bmp
ArchitecturesInstallIn64BitMode=x64
ChangesAssociations=Yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "bisq\bisq.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "bisq\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\bisq"; Filename: "{app}\bisq.exe"; IconFilename: "{app}\bisq.ico"; Check: returnTrue()
Name: "{commondesktop}\bisq"; Filename: "{app}\bisq.exe";  IconFilename: "{app}\bisq.ico"; Check: returnFalse()

[Run]
Filename: "{app}\bisq.exe"; Description: "{cm:LaunchProgram,bisq}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\bisq.exe"; Parameters: "-install -svcName ""bisq"" -svcDesc ""bisq"" -mainExe ""bisq.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\bisq.exe "; Parameters: "-uninstall -svcName bisq -stopOnUninstall"; Check: returnFalse()

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
