# Jörmungandr Inno Setup Installer Builder
# Apache 2.0 (c) 2025-2026 indoctrinatedrecluse

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path

Write-Host "=== Building Jörmungandr Windows Installer ===" -ForegroundColor Cyan

# Locate ISCC.exe
$isccCandidates = @(
    "iscc",
    "${env:ProgramFiles(x86)}\Inno Setup 6\ISCC.exe",
    "${env:ProgramFiles}\Inno Setup 6\ISCC.exe",
    "${env:LOCALAPPDATA}\Programs\Inno Setup 6\ISCC.exe"
)

$isccPath = $null
foreach ($candidate in $isccCandidates) {
    if (Get-Command $candidate -ErrorAction SilentlyContinue) {
        $isccPath = (Get-Command $candidate).Source
        break
    } elseif (Test-Path $candidate) {
        $isccPath = $candidate
        break
    }
}

if (-not $isccPath) {
    Write-Warning "Inno Setup Compiler (ISCC.exe) was not found on your system."
    Write-Host "To compile the Windows installer executable (Jormungandr-Setup-v1.0.0-x64.exe):" -ForegroundColor Yellow
    Write-Host "  1. Install Inno Setup 6: winget install JRSoftware.InnoSetup -e  OR  choco install innosetup"
    Write-Host "  2. Rerun: ./tools/build_installer.ps1"
    exit 0
}

Write-Host "Found Inno Setup Compiler: $isccPath" -ForegroundColor Green

$issFile = Join-Path $ProjectRoot "installer\jormungandr_setup.iss"
$distDir = Join-Path $ProjectRoot "dist"
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

Write-Host "Compiling $issFile..." -ForegroundColor Cyan
& $isccPath $issFile

Write-Host "=== Installer build finished successfully! Check dist/ ===" -ForegroundColor Green
