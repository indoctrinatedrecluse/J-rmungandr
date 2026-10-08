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

Write-Host "Building and installing jormungandr-bootstrap.jar..."
$bootstrapJar = Join-Path "$staging\lib" "jormungandr-bootstrap.jar"
Push-Location $resourcesDir
try {
    & 7z a -tzip $bootstrapJar "icons" "splash" "themes" "consents*.json" | Out-Null
} finally {
    Pop-Location
}

Write-Host "Creating bin directory and copying launcher binaries..."
New-Item -ItemType Directory -Path "$staging\bin" -Force | Out-Null
Copy-Item "$ideaHome\bin\*" -Destination "$staging\bin" -Force

# Overwrite icons in bin
Copy-Item "$resourcesDir\icons\jormungandr.svg" -Destination "$staging\bin\idea.svg" -Force
Copy-Item "$resourcesDir\icons\jormungandr.svg" -Destination "$staging\bin\jormungandr.svg" -Force
Copy-Item "$resourcesDir\icons\jormungandr.ico" -Destination "$staging\bin\idea.ico" -Force
Copy-Item "$resourcesDir\icons\jormungandr.ico" -Destination "$staging\bin\jormungandr.ico" -Force

Write-Host "Creating portable-data directory..."
New-Item -ItemType Directory -Path "$staging\portable-data\config\options" -Force | Out-Null
New-Item -ItemType Directory -Path "$staging\portable-data\system" -Force | Out-Null
New-Item -ItemType Directory -Path "$staging\portable-data\log" -Force | Out-Null

# 1. Update product-info.json
$productInfoPath = "$staging\product-info.json"
Copy-Item "$ideaHome\product-info.json" -Destination $productInfoPath -Force
$productInfoJson = Get-Content $productInfoPath -Raw | ConvertFrom-Json
$productInfoJson.name = "Jörmungandr"
$productInfoJson.productVendor = "indoctrinatedrecluse"
$productInfoJson.dataDirectoryName = "Jormungandr1.0"
$productInfoJson.svgIconPath = "bin/jormungandr.svg"

# Add jormungandr-bootstrap.jar to bootClassPathJarNames
if ($productInfoJson.launch[0].bootClassPathJarNames -notcontains "jormungandr-bootstrap.jar") {
    $productInfoJson.launch[0].bootClassPathJarNames += "jormungandr-bootstrap.jar"
}

# Update additionalJvmArguments
$newArgs = @()
foreach ($arg in $productInfoJson.launch[0].additionalJvmArguments) {
    if ($arg -like "-Didea.vendor.name=*") {
        $newArgs += "-Didea.vendor.name=indoctrinatedrecluse"
    } elseif ($arg -like "-Didea.paths.selector=*") {
        $newArgs += "-Didea.paths.selector=Jormungandr1.0"
    } else {
        $newArgs += $arg
    }
}
$newArgs += "-Didea.product.name=Jörmungandr"
$newArgs += "-Didea.application.name=Jörmungandr"
$productInfoJson.launch[0].additionalJvmArguments = $newArgs
$productInfoJson | ConvertTo-Json -Depth 10 | Set-Content $productInfoPath -Encoding UTF8
Write-Host "Updated product-info.json"

# 2. Write custom bin/idea.properties
$propsContent = @'
# Jörmungandr Portable IDE Properties
idea.config.path=${idea.home.path}/portable-data/config
idea.system.path=${idea.home.path}/portable-data/system
idea.plugins.path=${idea.home.path}/jormungandr-plugins
idea.log.path=${idea.home.path}/portable-data/log
idea.vendor.name=indoctrinatedrecluse
idea.product.name=Jörmungandr
idea.application.name=Jörmungandr
'@
Set-Content -Path "$staging\bin\idea.properties" -Value $propsContent -Encoding UTF8
Write-Host "Wrote bin/idea.properties"

# 3. Append VM options to bin/idea64.exe.vmoptions
$vmOptionsToAppend = @"

-Didea.vendor.name=indoctrinatedrecluse
-Didea.product.name=Jörmungandr
-Didea.application.name=Jörmungandr
-Didea.platform.prefix=Idea
-Didea.paths.selector=Jormungandr1.0
-Didea.required.plugins.id=org.jormungandr.ide
-Djb.consents.confirmation.enabled=false
-Deua.consents.confirmation.enabled=false
-Didea.initially.ask.config=false
-Dide.show.tips.on.startup=false
-Dide.mac.message.dialogs.as.sheets=false
-Dwsl.use.remote.agent.for.nio.filesystem=false
-Dwsl.enabled=false
-Dide.ijent.wsldefault=false
-Didea.wsl.support.enabled=false
-Dsplash=true
"@
Add-Content -Path "$staging\bin\idea64.exe.vmoptions" -Value $vmOptionsToAppend -Encoding UTF8
Copy-Item "$staging\bin\idea64.exe" -Destination "$staging\bin\jormungandr64.exe" -Force
Copy-Item "$staging\bin\idea64.exe.vmoptions" -Destination "$staging\bin\jormungandr64.exe.vmoptions" -Force
Write-Host "Updated bin/idea64.exe.vmoptions and created jormungandr64.exe"

# 4. Pre-populate consent in portable-data/config
$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$acceptedStr = "rsch.send.usage.stat:1.1:0:$nowMs;eap:2021.2:0:$nowMs;`n"
$cachedJson = '[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Surveys","printableName":"Feedback","accepted":"false"}]'

@(
    "$staging\portable-data\config\consentOptions",
    "$staging\portable-data\config\jormungandr\consentOptions",
    "$staging\portable-data\config\idea\consentOptions"
) | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
    Set-Content -Path (Join-Path $_ "accepted") -Value $acceptedStr -Encoding UTF8
    Set-Content -Path (Join-Path $_ "cached") -Value $cachedJson -Encoding UTF8
}

$otherXml = @"
<application>
  <component name="PropertyService"><![CDATA[{
  "keyToString": {
    "eua_accepted_version": "2.0",
    "privacy_policy_accepted_version": "2.0",
    "previous_eua_accepted_version": "2.0",
    "ask.about.tip.of.the.day": "false",
    "show.tips.on.startup": "false"
  }
}]]></component>
</application>
"@
Set-Content -Path "$staging\portable-data\config\options\other.xml" -Value $otherXml -Encoding UTF8
Write-Host "Pre-populated portable consent & preferences"

# 5. Add root launcher Jormungandr.bat
$launcherBat = Join-Path $staging "Jormungandr.bat"
$batContent = @"
@echo off
start "" "%~dp0bin\idea64.exe" %*
"@
Set-Content -Path $launcherBat -Value $batContent -Encoding ascii
Write-Host "Created Jormungandr.bat"

Write-Host "Test portable staging ready at $staging"
