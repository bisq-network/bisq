;This file will be executed next to the application bundle image
;I.e. current directory will contain folder Bisq with application files
;Note: This file must use UTF-8 encoding with BOM for the unicode custom messages to be displayed properly

#define SourceDir GetEnv('package_dir') + '\windows'
#define AppVersion GetEnv('version')
#define FileVersion GetEnv('file_version')
#define AppCopyrightYear GetDateTimeString('yyyy', '-', ':')

[Setup]
AppId={{bisq}}
AppName=Bisq
AppVersion={#AppVersion}
AppVerName=Bisq v{#AppVersion}
AppPublisher=Bisq
AppComments={cm:AppComments}
AppCopyright=Copyright (C) {#AppCopyrightYear}
AppPublisherURL=https://bisq.network
AppSupportURL=https://bisq.community
;AppUpdatesURL=https://bisq.network/downloads
VersionInfoVersion={#FileVersion}
VersionInfoDescription=Bisq Setup
VersionInfoCopyright=Copyright (C) {#AppCopyrightYear}
DefaultDirName={code:GetDefaultDirName}
DefaultGroupName=Bisq
DisableStartupPrompt=Yes
DisableWelcomePage=No
DisableDirPage=No
DisableProgramGroupPage=Yes
DisableReadyPage=No
DisableFinishedPage=No
;Optional License
LicenseFile=
;Windows 7 with Service Pack 1 or above
MinVersion=0,6.1.7601
OutputBaseFilename=Bisq-{#AppVersion}
Compression=lzma
SolidCompression=yes
PrivilegesRequired=lowest
SetupIconFile=Bisq\Bisq.ico
UninstallDisplayIcon={app}\Bisq.ico
UninstallDisplayName=Bisq
WizardImageFile={#SourceDir}\Bisq-setup-image.bmp
WizardImageStretch=No
WizardSmallImageFile=Bisq-setup-icon.bmp
ArchitecturesInstallIn64BitMode=x64
ShowLanguageDialog=No

[Languages]
Name: en; MessagesFile: "compiler:Default.isl"
Name: de; MessagesFile: "compiler:Languages\German.isl"
Name: fr; MessagesFile: "compiler:Languages\French.isl"
Name: sp; MessagesFile: "compiler:Languages\Spanish.isl"

[CustomMessages]
en.AppComments=A decentralized bitcoin exchange network
en.AppIsRunning=Bisq is running, please close it and run setup again.
en.SpecialAppPath=Your default install path appears to have special characters: %1%n%nThis may prevent Bisq from starting. See https://github.com/bisq-network/bisq/issues/3605 for more information.%n%nYou can either cancel this installation and install as a user without special characters in the username, or proceed with this installation using a different install path that does not contain special characters (e.g. %2).
de.AppComments=Ein dezentrales bitcoin-Austauschnetz
de.AppIsRunning=Bisq läuft, bitte schließen Sie es und führen Sie das Setup erneut aus.
de.SpecialAppPath=Ihr Standardinstallationspfad scheint Sonderzeichen zu enthalten: %1%n%nDies kann den Start von Bisq verhindern. Weitere Informationen finden Sie unter https://github.com/bisq-network/bisq/issues/3605.%n%nSie können diese Installation abbrechen und als Benutzer ohne Sonderzeichen im Benutzernamen installieren oder mit dieser Installation fortfahren, indem Sie einen anderen Installationspfad verwenden, der keine Sonderzeichen enthält (z. B. %2).
fr.AppComments=Un réseau d’échange bitcoin décentralisé
fr.AppIsRunning=Bisq est en cours d'exécution, fermez-le et exécutez à nouveau le programme d'installation.
fr.SpecialAppPath=Votre chemin d'installation par défaut semble comporter des caractères spéciaux: %1%n%nCela peut empêcher le démarrage de Bisq. Voir https://github.com/bisq-network/bisq/issues/3605 pour plus d'informations.%n%nVous pouvez annuler cette installation et l'installer en tant qu'utilisateur sans caractères spéciaux dans le nom d'utilisateur ou procéder à cette installation en utilisant un chemin d'installation différent ne contenant pas de caractères spéciaux (par exemple, %2).
sp.AppComments=Una red descentralizada de intercambio de bitcoin
sp.AppIsRunning=Bisq se está ejecutando, ciérrelo y vuelva a ejecutar la configuración.
sp.SpecialAppPath=Su ruta de instalación predeterminada parece tener caracteres especiales: %1%n%nEsto puede evitar que Bisq se inicie. Consulte https://github.com/bisq-network/bisq/issues/3605 para obtener más información.%n%nPuede cancelar esta instalación e instalar como usuario sin caracteres especiales en el nombre de usuario, o continuar con esta instalación utilizando una ruta de instalación diferente que no contenga caracteres especiales (por ejemplo, %2).

[Files]
Source: "Bisq\Bisq.exe"; DestDir: "{app}"; Flags: ignoreversion
Source: "Bisq\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\Bisq"; Filename: "{app}\Bisq.exe"; IconFilename: "{app}\Bisq.ico"
Name: "{userdesktop}\Bisq"; Filename: "{app}\Bisq.exe"; IconFilename: "{app}\Bisq.ico"

[Run]
Filename: "{app}\Bisq.exe"; Description: "{cm:LaunchProgram,Bisq}"; Flags: nowait postinstall skipifsilent

[Code]
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
  end;
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
    end;
    CreateDir(hiddenServiceBackupDir);
    DirectoryCopy(hiddenServiceDir, hiddenServiceBackupDir);
    DelTree(torDir, false, true, true);
    CreateDir(hiddenServiceDir);
    DirectoryCopy(hiddenServiceBackupDir, hiddenServiceDir);
  end;
end;

function PrepareToInstall(var NeedsRestart: Boolean): String;
begin
  DeleteOldAppDataDirectory;
  DeleteTorFiles;
  Result := '';
end;

function IsAppRunning(): Boolean;
var
  FSWbemLocator : Variant;
  FWMIService : Variant;
  FWbemObjectSet : Variant;
  ExecutablePath : String;
begin
  Result := False;
  ExecutablePath := Format('%s\Bisq\Bisq.exe', [ExpandConstant('{localappdata}')])
  StringChangeEx(ExecutablePath, '\', '\\', True);
  try
    FSWbemLocator := CreateOleObject('WBEMScripting.SWBEMLocator');
    FWMIService := FSWbemLocator.ConnectServer('localhost', 'root\CIMV2', '', '');
    FWbemObjectSet := FWMIService.ExecQuery(Format('SELECT Name FROM Win32_Process Where ExecutablePath="%s"', [ExecutablePath]));
    Result := (FWbemObjectSet.Count > 0);
    FWbemObjectSet := Unassigned;
    FWMIService := Unassigned;
    FSWbemLocator := Unassigned;
  except
  end;
end;

function isPathSpecial(Path : String): Boolean;
var
  I : Integer;
begin
  Result := not
    ((Length(Path) >= 3) and
    (Path[1] >= 'A') and (Path[1] <= 'Z') and
    (Path[2] = ':') and
    (Path[3] = '\'));
  if not Result then
  begin
    for I := 4 to Length(Path) do
    begin
      case Path[I] of
        '0'..'9', 'A'..'Z', 'a'..'z', '\', ' ', '.', '-', '_', '(', ')':
      else
        begin
          Result := True;
          Break;
        end;
      end;
    end;
  end;
end;

function InitializeSetup(): Boolean;
begin
  Result := True;
  if IsAppRunning() then begin
    MsgBox(ExpandConstant('{cm:AppIsRunning}'), mbCriticalError, MB_OK);
    Result := False;
  end;
end;

function InitializeUninstall(): Boolean;
begin
  Result := True;
  if IsAppRunning() then
  begin
    MsgBox(ExpandConstant('{cm:AppIsRunning}'), mbCriticalError, MB_OK);
    Result := False;
  end;
end;

procedure CurPageChanged(CurPageID: Integer);
var
  DefaultAppPath : String;
  AppPath : String;
begin
  DefaultAppPath := ExpandConstant('{localappdata}');
  if (CurPageID = wpSelectDir) and isPathSpecial(DefaultAppPath) then
  begin
    AppPath := ExpandConstant('{code:GetDefaultDirName}');
    MsgBox(FmtMessage(CustomMessage('SpecialAppPath'), [DefaultAppPath, AppPath]), mbInformation, MB_OK);
  end;
end;

function GetDefaultDirName(Param: String): String;
begin
  Result := Format('%s\Bisq', [ExpandConstant('{localappdata}')]);
  if isPathSpecial(Result) then
  begin
    Result := Format('%s\Bisq', [ExpandConstant('{%ProgramW6432}')]);
  end;
end;
