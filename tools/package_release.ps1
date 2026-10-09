# tools/package_release.ps1
# Builds and packages Jörmungandr standalone portable zip and plugin distribution.

$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$tag = "v1.0.0"
Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Packaging Jormungandr Release: $tag" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

$distDir = Join-Path $ProjectRoot "release-dist"
if (Test-Path $distDir) {
    Remove-Item $distDir -Recurse -Force
}
New-Item -ItemType Directory -Path $distDir -Force | Out-Null

# 1. Modular Plugin Package
$pluginZip = Get-ChildItem -Path "$ProjectRoot\modules\platform-shell\build\distributions\*.zip" | Select-Object -First 1
if (-not $pluginZip) {
    Write-Host "Building plugin distribution..." -ForegroundColor Yellow
    & "$ProjectRoot\gradlew.bat" buildPlugin --no-daemon
    $pluginZip = Get-ChildItem -Path "$ProjectRoot\modules\platform-shell\build\distributions\*.zip" | Select-Object -First 1
}

$destPlugin = Join-Path $distDir "Jormungandr-Plugin-$tag.zip"
Copy-Item -Path $pluginZip.FullName -Destination $destPlugin -Force
$pluginSize = [math]::Round((Get-Item $destPlugin).Length / 1MB, 2)
Write-Host "[OK] Packaged plugin: $destPlugin ($pluginSize MB)" -ForegroundColor Green

# 2. Standalone Portable Distribution
$ideaHome = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\8.11.1\transforms\*\transformed\ideaIC-*-win" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
$sandboxPlugins = Get-ChildItem -Path "$ProjectRoot\modules\platform-shell\build\idea-sandbox\IC-*\plugins" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1

if (-not $ideaHome -or -not $sandboxPlugins) {
    Write-Error "Could not locate transformed ideaIC home or sandbox plugins!"
}

Write-Host "Found IntelliJ Platform Home: $($ideaHome.FullName)" -ForegroundColor Gray
Write-Host "Found Sandbox Plugins: $($sandboxPlugins.FullName)" -ForegroundColor Gray

$stagingRoot = Join-Path $distDir "staging"
$stagingDir = Join-Path $stagingRoot "Jormungandr-$tag"
New-Item -ItemType Directory -Path $stagingDir -Force | Out-Null
$resourcesDir = (Resolve-Path "$ProjectRoot\modules\platform-shell\src\main\resources").Path

Write-Host "Copying IntelliJ platform base into staging..." -ForegroundColor Yellow
Copy-Item -Path "$($ideaHome.FullName)\*" -Destination $stagingDir -Recurse -Force

Write-Host "Copying Jormungandr plugins into bundled plugins directory..." -ForegroundColor Yellow
$bundledPluginsDir = Join-Path $stagingDir "plugins"
$targetPluginsDir = Join-Path $stagingDir "jormungandr-plugins"
New-Item -ItemType Directory -Path $targetPluginsDir -Force | Out-Null

Get-ChildItem -Path $sandboxPlugins.FullName -Directory | ForEach-Object {
    $pluginName = $_.Name
    Write-Host "  -> Bundling plugin: $pluginName" -ForegroundColor Cyan
    Copy-Item -Path $_.FullName -Destination (Join-Path $bundledPluginsDir $pluginName) -Recurse -Force
    Copy-Item -Path $_.FullName -Destination (Join-Path $targetPluginsDir $pluginName) -Recurse -Force
}

Write-Host "Patching portable distribution via patch_portable_ide.py..." -ForegroundColor Yellow
python "$ProjectRoot\tools\patch_portable_ide.py" "$stagingDir" "$resourcesDir" "$tag"

# Compress with 7-Zip
$portableZip = Join-Path $distDir "Jormungandr-$tag-windows-x64.zip"
Write-Host "Compressing standalone portable package to $portableZip..." -ForegroundColor Yellow

# Archive from stagingRoot so the archive contains Jormungandr-v1.0.0/... folder
Push-Location $stagingRoot
try {
    & 7z a -tzip -mx=5 $portableZip "Jormungandr-$tag"
} finally {
    Pop-Location
}

if (Test-Path $portableZip) {
    $zipSize = [math]::Round((Get-Item $portableZip).Length / 1MB, 2)
    Write-Host "[OK] Standalone portable package created successfully: $portableZip ($zipSize MB)" -ForegroundColor Green
} else {
    Write-Error "Failed to produce $portableZip"
}

# Clean staging directory to reclaim disk space
Remove-Item -Path $stagingRoot -Recurse -Force -ErrorAction SilentlyContinue

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  Packaging Complete for $tag!" -ForegroundColor Green
Write-Host "============================================================" -ForegroundColor Cyan
