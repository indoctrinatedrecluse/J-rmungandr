<#
.SYNOPSIS
    Jörmungandr Windows Registry Cleaner.

.DESCRIPTION
    Safely cleans Windows Registry keys and Java Preferences nodes created exclusively by
    Jörmungandr (and indoctrinatedrecluse).

    CRITICAL SAFETY GUARANTEE:
    This script explicitly protects all JetBrains Enterprise / Professional products (DataGrip,
    GoLand, PyCharm, Rider, WebStorm, CLion, IntelliJ IDEA Ultimate, Toolbox, JetProfile, etc.).
    It will NEVER touch or delete any JetBrains product trees or configurations.

.PARAMETER WhatIf
    Performs a dry-run scan showing what keys and values would be removed without making changes.

.PARAMETER Force
    Deletes keys without interactive confirmation.

.PARAMETER IncludeSharedEua
    Optionally removes the shared upstream IntelliJ Platform EUA entry
    (HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\privacy_policy\eua_accepted_version).
    WARNING: Only use this if you want to test the first-run agreement dialog prompt.

.PARAMETER Help
    Displays help and usage documentation.

.EXAMPLE
    .\tools\clear_registry.ps1 -WhatIf
    .\tools\clear_registry.ps1
    .\tools\clear_registry.ps1 -Force
    .\tools\clear_registry.ps1 -IncludeSharedEua
#>

[CmdletBinding(SupportsShouldProcess)]
param(
    [switch]$DryRun,
    [switch]$Force,
    [switch]$IncludeSharedEua,
    [switch]$Help
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

# Ensure UTF-8 output encoding
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

function Write-Header {
    param([string]$Message)
    Write-Host "`n============================================================" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
}

function Write-Info    { param([string]$Msg) Write-Host "[INFO]      $Msg" -ForegroundColor Gray }
function Write-Ok      { param([string]$Msg) Write-Host "[OK]        $Msg" -ForegroundColor Green }
function Write-Warn    { param([string]$Msg) Write-Host "[WARN]      $Msg" -ForegroundColor Yellow }
function Write-Action  { param([string]$Msg) Write-Host "[CLEANED]   $Msg" -ForegroundColor Magenta }
function Write-Skipped { param([string]$Msg) Write-Host "[PROTECTED] $Msg" -ForegroundColor DarkCyan }
function Write-DryRun  { param([string]$Msg) Write-Host "[DRY-RUN]   $Msg" -ForegroundColor Yellow }

if ($Help) {
    Write-Header "Jörmungandr Registry Cleaner"
    Write-Host "Usage: .\tools\clear_registry.ps1 [OPTIONS]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -WhatIf           Preview keys that would be removed without modifying registry"
    Write-Host "  -Force            Remove keys without interactive confirmation prompt"
    Write-Host "  -IncludeSharedEua Also reset the shared JetBrains privacy_policy bridge key"
    Write-Host "  -Help             Display this help message"
    exit 0
}

Write-Header "🐍 Jörmungandr Registry Cleaner"

# ---------------------------------------------------------------------------
# Strict Protection Rules for JetBrains Enterprise Products
# ---------------------------------------------------------------------------
$ProtectedPaths = @(
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\db",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\go",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\py",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\rd",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\jetprofile",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\region",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\idea",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\clion",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\webstorm",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\phpstorm",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\rubymine",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\resharper",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\fleet",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\toolbox",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\google",
    "HKCU:\SOFTWARE\JetBrains"
)

Write-Info "Verifying protected product registry paths (Enterprise JetBrains shields active)..."
foreach ($prot in $ProtectedPaths) {
    if (Test-Path $prot) {
        Write-Skipped "Protected product tree intact: $prot"
    }
}

# ---------------------------------------------------------------------------
# Target Jörmungandr Registry Keys (Safe to Clean)
# ---------------------------------------------------------------------------
$TargetRegistryKeys = @(
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jormungandr",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\indoctrinatedrecluse",
    "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\jormungandr",
    "HKCU:\SOFTWARE\Jormungandr",
    "HKCU:\SOFTWARE\indoctrinatedrecluse\Jormungandr",
    "HKCU:\SOFTWARE\indoctrinatedrecluse",
    "HKLM:\SOFTWARE\Jormungandr"
)

$FoundKeys = @()
foreach ($key in $TargetRegistryKeys) {
    if (Test-Path $key) {
        $FoundKeys += $key
    }
}

Write-Host "`n--- Scan Results ---" -ForegroundColor White
if ($FoundKeys.Count -eq 0) {
    Write-Ok "No Jörmungandr-specific registry keys found. Registry is already clean."
} else {
    Write-Info "Found $($FoundKeys.Count) Jörmungandr target key(s):"
    foreach ($k in $FoundKeys) {
        Write-Host "  -> $k" -ForegroundColor Yellow
    }
}

# Optional Shared EUA Bridge Handling
$SharedEuaPath = "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\privacy_policy"
$SharedEuaExists = Test-Path $SharedEuaPath

if ($IncludeSharedEua) {
    Write-Warn "Shared EUA bridge cleanup requested (-IncludeSharedEua)."
    if ($SharedEuaExists) {
        Write-Host "  -> $SharedEuaPath (value: eua_accepted_version)" -ForegroundColor Magenta
    }
} else {
    Write-Info "Shared JetBrains privacy_policy key is PRESERVED (use -IncludeSharedEua to reset)."
}

# ---------------------------------------------------------------------------
# Execution / Dry-Run
# ---------------------------------------------------------------------------
if ($DryRun -or $WhatIfPreference) {
    Write-Header "DRY-RUN SCAN COMPLETE (No changes were made)"
    foreach ($k in $FoundKeys) {
        Write-DryRun "Would delete: $k"
    }
    if ($IncludeSharedEua -and $SharedEuaExists) {
        Write-DryRun "Would remove value 'eua_accepted_version' from: $SharedEuaPath"
    }
    exit 0
}

if ($FoundKeys.Count -eq 0 -and (-not $IncludeSharedEua -or -not $SharedEuaExists)) {
    Write-Ok "Nothing to clean. Exiting."
    exit 0
}

# Confirmation check if not -Force
if (-not $Force) {
    $confirm = Read-Host "`nAre you sure you want to delete these Jörmungandr keys? (y/N)"
    if ($confirm -notmatch '^[yY]') {
        Write-Warn "Operation cancelled by user."
        exit 0
    }
}

Write-Host "`n--- Deleting Jörmungandr Registry Keys ---" -ForegroundColor White
$cleanedCount = 0

foreach ($key in $FoundKeys) {
    try {
        # Double check that we NEVER delete protected paths or jetbrains root
        if ($key -eq "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains" -or
            $key -eq "HKCU:\SOFTWARE\JetBrains" -or
            $key -like "*\jetbrains\db*" -or
            $key -like "*\jetbrains\go*" -or
            $key -like "*\jetbrains\py*" -or
            $key -like "*\jetbrains\rd*") {
            Write-Warn "Refusing to delete protected path: $key"
            continue
        }

        Remove-Item -Path $key -Recurse -Force -ErrorAction Stop
        Write-Action "Deleted: $key"
        $cleanedCount++
    } catch {
        Write-Warn "Failed to delete $key : $_"
    }
}

if ($IncludeSharedEua -and $SharedEuaExists) {
    try {
        if (Get-ItemProperty -Path $SharedEuaPath -Name "eua_accepted_version" -ErrorAction SilentlyContinue) {
            Remove-ItemProperty -Path $SharedEuaPath -Name "eua_accepted_version" -Force -ErrorAction Stop
            Write-Action "Removed 'eua_accepted_version' from $SharedEuaPath"
        }
        if (Get-ItemProperty -Path $SharedEuaPath -Name "privacy_policy_accepted_version" -ErrorAction SilentlyContinue) {
            Remove-ItemProperty -Path $SharedEuaPath -Name "privacy_policy_accepted_version" -Force -ErrorAction Stop
            Write-Action "Removed 'privacy_policy_accepted_version' from $SharedEuaPath"
        }
    } catch {
        Write-Warn "Could not clear shared EUA properties: $_"
    }
}

Write-Header "Registry Clean Complete"
Write-Ok "Cleaned $cleanedCount Jörmungandr registry key(s)."
Write-Ok "All JetBrains Enterprise products (DataGrip, GoLand, PyCharm, Rider) remain completely protected."
