$ErrorActionPreference = "Stop"

$ProjectRoot = (Resolve-Path "$PSScriptRoot\..").Path
$staging = Join-Path $ProjectRoot "build\test-portable"
$ideaHome = "C:\Users\RECLUSE\.gradle\caches\8.11.1\transforms\4674ac0b0e96ecef9b50b9a631945e04\transformed\ideaIC-2024.3.2-win"
$sandboxPlugins = Join-Path $ProjectRoot "modules\platform-shell\build\idea-sandbox\IC-2024.3.2\plugins"
$resourcesDir = Join-Path $ProjectRoot "modules\platform-shell\src\main\resources"

Write-Host "Cleaning previous test staging..."
if (Test-Path $staging) {
    Get-ChildItem $staging | Where-Object { $_.Attributes -match "ReparsePoint" } | ForEach-Object { $_.Delete() }
    Remove-Item $staging -Recurse -Force
}
New-Item -ItemType Directory -Path $staging -Force | Out-Null

Write-Host "Creating platform directories..."
New-Item -ItemType Directory -Path "$staging\lib" -Force | Out-Null
Copy-Item "$ideaHome\lib\*" -Destination "$staging\lib" -Recurse -Force

New-Item -ItemType Junction -Path "$staging\jbr" -Target "$ideaHome\jbr" | Out-Null
New-Item -ItemType Junction -Path "$staging\plugins" -Target "$ideaHome\plugins" | Out-Null
New-Item -ItemType Junction -Path "$staging\modules" -Target "$ideaHome\modules" | Out-Null
if (Test-Path "$ideaHome\license") {
    New-Item -ItemType Junction -Path "$staging\license" -Target "$ideaHome\license" | Out-Null
}

Write-Host "Junctioning Jörmungandr plugins..."
New-Item -ItemType Junction -Path "$staging\jormungandr-plugins" -Target $sandboxPlugins | Out-Null
$testPortablePlugins = Join-Path $staging "portable-data\plugins"
New-Item -ItemType Directory -Path $testPortablePlugins -Force | Out-Null
Copy-Item "$sandboxPlugins\*" -Destination $testPortablePlugins -Recurse -Force

Write-Host "Creating bin directory and copying launcher binaries..."
New-Item -ItemType Directory -Path "$staging\bin" -Force | Out-Null
Copy-Item "$ideaHome\bin\*" -Destination "$staging\bin" -Force
Copy-Item "$ideaHome\product-info.json" -Destination "$staging\product-info.json" -Force

Write-Host "Patching portable distribution via python tools/patch_portable_ide.py..."
python "$ProjectRoot\tools\patch_portable_ide.py" "$staging" "$resourcesDir" "1.0.0"

Write-Host "Test portable staging ready at $staging"
