;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Bisq with application files
[Setup]
AppId={{bisq}}
AppName=Bisq
AppVersion=0.6.0
AppVerName=Bisq
AppPublisher=Bisq
AppComments=Bisq
AppCopyright=Copyright (C) 2016
AppPublisherURL=https://bisq.network
AppSupportURL=https://bisq.network
;AppUpdatesURL=http://java.com/
DefaultDirName={localappdata}\Bisq
DisableStartupPrompt=Yes
DisableDirPage=Yes
DisableProgramGroupPage=Yes
DisableReadyPage=Yes
DisableFinishedPage=Yes
DisableWelcomePage=Yes
DefaultGroupName=Bisq
;Optional License
LicenseFile=
;WinXP or above
MinVersion=0,5.1
OutputBaseFilename=Bisq
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=Bisq.ico
UninstallDisplayIcon={app}\Bisq.ico
UninstallDisplayName=Bisq
WizardImageStretch=No
WizardSmallImageFile=Bisq-setup-icon.bmp
ArchitecturesInstallIn64BitMode=x64
ChangesAssociations=Yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Files]
Source: "Bisq\Bisq.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "Bisq\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Bisq"; Filename: "{app}\Bisq.exe"; IconFilename: "{app}\Bisq.ico"; Check: returnTrue()
Name: "{commondesktop}\Bisq"; Filename: "{app}\Bisq.exe";  IconFilename: "{app}\Bisq.ico"; Check: returnFalse()

[Run]
Filename: "{app}\Bisq.exe"; Description: "{cm:LaunchProgram,Bisq}"; Flags: nowait postinstall skipifsilent; Check: returnTrue()
Filename: "{app}\Bisq.exe"; Parameters: "-install -svcName ""Bisq"" -svcDesc ""Bisq"" -mainExe ""Bisq.exe""  "; Check: returnFalse()

[UninstallRun]
Filename: "{app}\Bisq.exe "; Parameters: "-uninstall -svcName Bisq -stopOnUninstall"; Check: returnFalse()

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
