; Script generated for Jörmungandr Modular IDE Setup
; Apache 2.0 (c) 2025-2026 indoctrinatedrecluse

#define MyAppName "Jörmungandr"
#define MyAppVersion "1.0.0"
#define MyAppPublisher "indoctrinatedrecluse"
#define MyAppURL "https://github.com/indoctrinatedrecluse/Jormungandr"
#define MyAppExeName "bin\jormungandr64.exe"
#define MyAppIcon "assets\icons\jormungandr.ico"

#ifndef SourceDir
  #define SourceDir "..\build\test-portable"
#endif

[Setup]
AppId={{E68A4870-E215-4D02-9908-469F54F70381}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
AppPublisherURL={#MyAppURL}
AppSupportURL={#MyAppURL}/issues
AppUpdatesURL={#MyAppURL}/releases
DefaultDirName={autopf}\{#MyAppName}
DefaultGroupName={#MyAppName}
AllowNoIcons=yes
OutputDir=..\dist
OutputBaseFilename=Jormungandr-Setup-v{#MyAppVersion}-x64
SetupIconFile=..\assets\icons\jormungandr.ico
Compression=lzma2/ultra64
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
ChangesEnvironment=yes
ChangesAssociations=yes

[Languages]
Name: "english"; MessagesFile: "compiler:Default.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; GroupDescription: "{cm:AdditionalIcons}"
Name: "associateipynb"; Description: "Associate with Jupyter Notebooks (.ipynb)"; GroupDescription: "File Associations:"
Name: "associateparquet"; Description: "Associate with Apache Parquet Datasets (.parquet)"; GroupDescription: "File Associations:"
Name: "associatearrow"; Description: "Associate with Apache Arrow / Feather (.arrow, .feather)"; GroupDescription: "File Associations:"
Name: "addtopath"; Description: "Add Jörmungandr launcher to System PATH"; GroupDescription: "System Environment:"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: ignoreversion recursesubdirs createallsubdirs

[Icons]
Name: "{group}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\bin\jormungandr.ico"
Name: "{group}\{cm:UninstallProgram,{#MyAppName}}"; Filename: "{uninstallexe}"
Name: "{autodesktop}\{#MyAppName}"; Filename: "{app}\{#MyAppExeName}"; IconFilename: "{app}\bin\jormungandr.ico"; Tasks: desktopicon

[Registry]
; File associations - .ipynb
Root: HKA; Subkey: "Software\Classes\.ipynb"; ValueType: string; ValueName: ""; ValueData: "Jormungandr.Ipynb"; Flags: uninsdeletevalue; Tasks: associateipynb
Root: HKA; Subkey: "Software\Classes\Jormungandr.Ipynb"; ValueType: string; ValueName: ""; ValueData: "Jupyter Notebook"; Flags: uninsdeletekey; Tasks: associateipynb
Root: HKA; Subkey: "Software\Classes\Jormungandr.Ipynb\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\bin\jormungandr.ico,0"; Tasks: associateipynb
Root: HKA; Subkey: "Software\Classes\Jormungandr.Ipynb\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: associateipynb

; File associations - .parquet
Root: HKA; Subkey: "Software\Classes\.parquet"; ValueType: string; ValueName: ""; ValueData: "Jormungandr.Parquet"; Flags: uninsdeletevalue; Tasks: associateparquet
Root: HKA; Subkey: "Software\Classes\Jormungandr.Parquet"; ValueType: string; ValueName: ""; ValueData: "Apache Parquet Dataset"; Flags: uninsdeletekey; Tasks: associateparquet
Root: HKA; Subkey: "Software\Classes\Jormungandr.Parquet\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\bin\jormungandr.ico,0"; Tasks: associateparquet
Root: HKA; Subkey: "Software\Classes\Jormungandr.Parquet\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: associateparquet

; File associations - .arrow & .feather
Root: HKA; Subkey: "Software\Classes\.arrow"; ValueType: string; ValueName: ""; ValueData: "Jormungandr.Arrow"; Flags: uninsdeletevalue; Tasks: associatearrow
Root: HKA; Subkey: "Software\Classes\.feather"; ValueType: string; ValueName: ""; ValueData: "Jormungandr.Arrow"; Flags: uninsdeletevalue; Tasks: associatearrow
Root: HKA; Subkey: "Software\Classes\Jormungandr.Arrow"; ValueType: string; ValueName: ""; ValueData: "Apache Arrow Table"; Flags: uninsdeletekey; Tasks: associatearrow
Root: HKA; Subkey: "Software\Classes\Jormungandr.Arrow\DefaultIcon"; ValueType: string; ValueName: ""; ValueData: "{app}\bin\jormungandr.ico,0"; Tasks: associatearrow
Root: HKA; Subkey: "Software\Classes\Jormungandr.Arrow\shell\open\command"; ValueType: string; ValueName: ""; ValueData: """{app}\{#MyAppExeName}"" ""%1"""; Tasks: associatearrow

; Add to PATH
Root: HKLM; Subkey: "SYSTEM\CurrentControlSet\Control\Session Manager\Environment"; \
    ValueType: expandsz; ValueName: "Path"; ValueData: "{olddata};{app}\bin"; \
    Check: NeedsAddPath(ExpandConstant('{app}\bin')); Tasks: addtopath

[Code]
function NeedsAddPath(Param: string): boolean;
var
  OrigPath: string;
begin
  if not RegQueryStringValue(HKEY_LOCAL_MACHINE,
    'SYSTEM\CurrentControlSet\Control\Session Manager\Environment',
    'Path', OrigPath)
  then begin
    Result := True;
    exit;
  end;
  Result := Pos(';' + UpperCase(Param) + ';', ';' + UpperCase(OrigPath) + ';') = 0;
end;

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "{cm:LaunchProgram,{#StringChange(MyAppName, '&', '&&')}}"; Flags: nowait postinstall skipifsilent
