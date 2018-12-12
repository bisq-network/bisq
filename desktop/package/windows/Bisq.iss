;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Bisq with application files
[Setup]

AppId={{bisq}}
AppName=Bisq
AppVersion=0.9.0
AppVerName=Bisq
AppPublisher=Bisq
AppComments=Bisq
AppCopyright=Copyright (C) 2018
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
SetupIconFile=Bisq\Bisq.ico
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

procedure DirectoryCopy(SourcePath, DestPath: string);
var
  FindRec: TFindRec;
  SourceFilePath: string;
  DestFilePath: string;
begin
  if FindFirst(SourcePath + '\*', FindRec) then
  begin
    try
      repeat
        if (FindRec.Name <> '.') and (FindRec.Name <> '..') then
        begin
          SourceFilePath := SourcePath + '\' + FindRec.Name;
          DestFilePath := DestPath + '\' + FindRec.Name;
          if FindRec.Attributes and FILE_ATTRIBUTE_DIRECTORY = 0 then
          begin
            if FileCopy(SourceFilePath, DestFilePath, False) then
            begin
              Log(Format('Copied %s to %s', [SourceFilePath, DestFilePath]));
            end
              else
            begin
              Log(Format('Failed to copy %s to %s', [SourceFilePath, DestFilePath]));
            end;
          end
            else
          begin
            if DirExists(DestFilePath) or CreateDir(DestFilePath) then
            begin
              Log(Format('Created %s', [DestFilePath]));
              DirectoryCopy(SourceFilePath, DestFilePath);
            end
              else
            begin
              Log(Format('Failed to create %s', [DestFilePath]));
            end;
          end;
        end;
      until not FindNext(FindRec);
    finally
      FindClose(FindRec);
    end;
  end
    else
  begin
    Log(Format('Failed to list %s', [SourcePath]));
  end;
end;

//Delete old app directory to prevent issues during update
procedure DeleteOldAppDataDirectory;
var
  entry: String;
begin
  entry := ExpandConstant('{localappdata}') + '\Bisq\';
  if DirExists(entry) then begin
    DelTree(entry, true, true, true);
  end
end;

procedure DeleteTorFiles;
var
  mainnetDir: String;
  torDir: String;
  hiddenServiceDir: String;
  hiddenServiceBackupDir : String;
begin
  mainnetDir := ExpandConstant('{userappdata}') + '\Bisq\btc_mainnet';
  torDir := mainnetDir + '\tor\*';
  hiddenServiceDir := mainnetDir + '\tor\hiddenservice';
  hiddenServiceBackupDir := mainnetDir + '\hiddenservice_backup';
  if DirExists(hiddenServiceDir) then begin
   if DirExists(hiddenServiceBackupDir) then begin
     DelTree(hiddenServiceBackupDir, true, true, true);
   end
   CreateDir(hiddenServiceBackupDir);
   DirectoryCopy(hiddenServiceDir, hiddenServiceBackupDir);
   DelTree(torDir, false, true, true);
   CreateDir(hiddenServiceDir);
   DirectoryCopy(hiddenServiceBackupDir, hiddenServiceDir);
  end
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  DeleteOldAppDataDirectory;
  DeleteTorFiles;
  Result := '';
end;

function InitializeSetup(): Boolean;
begin
// Possible future improvements:
//   if version less or same => just launch app
//   if upgrade => check if same app is running and wait for it to exit
//   Add pack200/unpack200 support?
  Result := True;
end;
