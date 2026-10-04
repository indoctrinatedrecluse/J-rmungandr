<#
.SYNOPSIS
    Direct Native Launcher for Jörmungandr IDE (Windows PowerShell).

.DESCRIPTION
    Launches the compiled Jörmungandr IDE directly using the transformed IntelliJ
    Platform JBR runtime, bypassing Gradle execution and daemon overhead.
    Starts as a native desktop application with full GUI visibility.

.PARAMETER Console
    If set, launches using java.exe with console output attached instead of javaw.exe.

.EXAMPLE
    .\tools\launch_ide.ps1
    .\tools\launch_ide.ps1 -Console
#>

[CmdletBinding()]
param(
    [switch]$Console
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path

Write-Host "============================================================" -ForegroundColor Cyan
Write-Host "  🐍 Launching Jörmungandr IDE (Direct Mode)" -ForegroundColor Cyan
Write-Host "============================================================" -ForegroundColor Cyan

# 1. Locate sandbox and plugin artifact
$sandboxDir = Get-ChildItem -Path (Join-Path $ProjectRoot "modules\platform-shell\build\idea-sandbox\IC-*") -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $sandboxDir) {
    Write-Host "[FAIL] Sandbox directory not found. Please run '.\tools\run.ps1' to build the project first." -ForegroundColor Red
    exit 1
}

$pluginJar = Get-ChildItem -Path (Join-Path $sandboxDir.FullName "plugins\platform-shell\lib\platform-shell-*.jar") -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $pluginJar) {
    Write-Host "[FAIL] Built Jörmungandr plugin not found in sandbox: $($sandboxDir.FullName)" -ForegroundColor Red
    Write-Host "[INFO] Please run '.\tools\run.ps1' to compile the IDE plugin first." -ForegroundColor Gray
    exit 1
}

Write-Host "[OK]   Found Jörmungandr sandbox: $($sandboxDir.FullName)" -ForegroundColor Green
Write-Host "[OK]   Found target plugin: $($pluginJar.Name)" -ForegroundColor Green

# 2. Locate Platform Home & JBR
$ideaHome = Get-ChildItem -Path "$env:USERPROFILE\.gradle\caches\8.11.1\transforms\*\transformed\ideaIC-*-win" -Directory -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $ideaHome) {
    Write-Host "[FAIL] Transformed IntelliJ Platform home not found in Gradle cache." -ForegroundColor Red
    exit 1
}

$binName = if ($Console) { "java.exe" } else { "javaw.exe" }
$javaExe = Join-Path $ideaHome.FullName "jbr\bin\$binName"
if (-not (Test-Path $javaExe)) {
    $javaExe = Join-Path $ideaHome.FullName "jbr\bin\java.exe"
}

Write-Host "[OK]   Found runtime: $javaExe" -ForegroundColor Green

# 3. Configure Paths & Flags
$configDir = Join-Path $sandboxDir.FullName "config"
$systemDir = Join-Path $sandboxDir.FullName "system"
$pluginsDir = Join-Path $sandboxDir.FullName "plugins"
$logDir = Join-Path $sandboxDir.FullName "log"

# Build Classpath from Platform lib/
$libJars = (Get-ChildItem -Path (Join-Path $ideaHome.FullName "lib") -Filter "*.jar" | Select-Object -ExpandProperty FullName) -join ";"

$vmArgs = @(
    "-Xms256m",
    "-Xmx2048m",
    "-XX:ReservedCodeCacheSize=512m",
    "-Didea.vendor.name=indoctrinatedrecluse",
    "-Didea.platform.prefix=Idea",
    "-Didea.paths.selector=IdeaIC2024.3",
    "-Didea.config.path=$configDir",
    "-Didea.system.path=$systemDir",
    "-Didea.plugins.path=$pluginsDir",
    "-Didea.log.path=$logDir",
    "-Didea.is.internal=true",
    "-Didea.plugin.in.sandbox.mode=true",
    "-Didea.required.plugins.id=org.jormungandr.ide",
    "-Djb.consents.confirmation.enabled=false",
    "-Deua.consents.confirmation.enabled=false",
    "-Didea.initially.ask.config=false",
    "-Dide.show.tips.on.startup=false",
    "-Dide.mac.message.dialogs.as.sheets=false",
    "-Dwsl.use.remote.agent.for.nio.filesystem=false",
    "-Dwsl.enabled=false",
    "-Dide.ijent.wsldefault=false",
    "-Didea.wsl.support.enabled=false",
    "-Dsun.java2d.uiScale.enabled=true",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
    "-cp", $libJars,
    "com.intellij.idea.Main"
)

Write-Host "[INFO] Launching Jörmungandr process ($binName)..." -ForegroundColor Gray
Write-Host "[INFO] Process will appear in Task Manager under: $binName" -ForegroundColor Gray

if ($Console) {
    & $javaExe @vmArgs
} else {
    $proc = Start-Process -FilePath $javaExe -ArgumentList $vmArgs -PassThru
    Write-Host "[OK]   Jörmungandr launched successfully! (PID: $($proc.Id))" -ForegroundColor Green
}
