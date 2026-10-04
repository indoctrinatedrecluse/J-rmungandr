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

# 3. Ensure Sandbox Pre-Configuration (EULA & Preferences)
$configDir = Join-Path $sandboxDir.FullName "config"
$systemDir = Join-Path $sandboxDir.FullName "system"
$pluginsDir = Join-Path $sandboxDir.FullName "plugins"
$logDir = Join-Path $sandboxDir.FullName "log"

$consentDir = Join-Path $configDir "consentOptions"
if (-not (Test-Path $consentDir)) { New-Item -ItemType Directory -Path $consentDir -Force | Out-Null }
$consentFile = Join-Path $consentDir "accepted"
Set-Content -Path $consentFile -Value "rsch.send.usage.stat:1.1:0:$([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds())`n" -Encoding UTF8

$optionsDir = Join-Path $configDir "options"
if (-not (Test-Path $optionsDir)) { New-Item -ItemType Directory -Path $optionsDir -Force | Out-Null }

$otherXml = Join-Path $optionsDir "other.xml"
if (-not (Test-Path $otherXml)) {
    $otherContent = @"
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
    Set-Content -Path $otherXml -Value $otherContent -Encoding UTF8
}

$generalLocalXml = Join-Path $optionsDir "ide.general.local.xml"
if (-not (Test-Path $generalLocalXml)) {
    $genContent = @"
<application>
  <component name="GeneralLocalSettings">
    <option name="showTipsOnStartup" value="false" />
  </component>
</application>
"@
    Set-Content -Path $generalLocalXml -Value $genContent -Encoding UTF8
}

# 4. Build Complete Classpath and VM Arguments
$ideaHomePath = $ideaHome.FullName -replace '\\', '/'
$jnaPath = "$ideaHomePath/lib/jna/amd64"
$pty4jPath = "$ideaHomePath/lib/pty4j"
$moduleDescriptors = "$ideaHomePath/modules/module-descriptors.jar"

# Build Classpath: platform-loader.jar must be first
$libDir = Join-Path $ideaHome.FullName "lib"
$libJars = @(
    (Join-Path $libDir "platform-loader.jar"),
    (Join-Path $libDir "util-8.jar"),
    (Join-Path $libDir "util.jar"),
    (Join-Path $libDir "app-client.jar"),
    (Join-Path $libDir "util_rt.jar"),
    (Join-Path $libDir "opentelemetry.jar"),
    (Join-Path $libDir "app.jar"),
    (Join-Path $libDir "lib-client.jar"),
    (Join-Path $libDir "stats.jar"),
    (Join-Path $libDir "jps-model.jar"),
    (Join-Path $libDir "external-system-rt.jar"),
    (Join-Path $libDir "rd.jar"),
    (Join-Path $libDir "bouncy-castle.jar"),
    (Join-Path $libDir "protobuf.jar"),
    (Join-Path $libDir "intellij-test-discovery.jar"),
    (Join-Path $libDir "forms_rt.jar"),
    (Join-Path $libDir "lib.jar"),
    (Join-Path $libDir "externalProcess-rt.jar"),
    (Join-Path $libDir "groovy.jar"),
    (Join-Path $libDir "annotations.jar"),
    (Join-Path $libDir "idea_rt.jar"),
    (Join-Path $libDir "jsch-agent.jar"),
    (Join-Path $libDir "kotlinx-coroutines-slf4j-1.8.0-intellij.jar"),
    (Join-Path $libDir "nio-fs.jar"),
    (Join-Path $libDir "trove.jar")
) | Where-Object { Test-Path $_ }
$cpString = ($libJars -join ";")

$vmArgs = @(
    "-Xms256m",
    "-Xmx2048m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:-OmitStackTraceInFastThrow",
    "-XX:CICompilerCount=2",
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
    "-Dsun.io.useCanonCaches=false",
    "-Djna.boot.library.path=$jnaPath",
    "-Djna.noclasspath=true",
    "-Djna.nosys=true",
    "-Dpty4j.preferred.native.folder=$pty4jPath",
    "-Dintellij.platform.runtime.repository.path=$moduleDescriptors",
    "-Djava.nio.file.spi.DefaultFileSystemProvider=com.intellij.platform.core.nio.fs.MultiRoutingFileSystemProvider",
    "-Djava.security.manager=com.intellij.platform.core.nio.fs.CoreBootstrapSecurityManager",
    "-Djava.system.class.loader=com.intellij.util.lang.PathClassLoader",
    "-Djava.util.zip.use.nio.for.zip.file.access=true",
    "-Djdk.attach.allowAttachSelf=true",
    "-Djdk.module.illegalAccess.silent=true",
    "-Djdk.nio.maxCachedBufferSize=2097152",
    "-Dsplash=true"
)

$agentJar = Join-Path $ProjectRoot "modules\platform-shell\build\coroutines-javaagent.jar"
if (Test-Path $agentJar) {
    $vmArgs += "-javaagent:$agentJar"
}

$addOpens = @(
    "--add-opens=java.base/java.io=ALL-UNNAMED",
    "--add-opens=java.base/java.lang=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.ref=ALL-UNNAMED",
    "--add-opens=java.base/java.lang.reflect=ALL-UNNAMED",
    "--add-opens=java.base/java.net=ALL-UNNAMED",
    "--add-opens=java.base/java.nio=ALL-UNNAMED",
    "--add-opens=java.base/java.nio.charset=ALL-UNNAMED",
    "--add-opens=java.base/java.text=ALL-UNNAMED",
    "--add-opens=java.base/java.time=ALL-UNNAMED",
    "--add-opens=java.base/java.util=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED",
    "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
    "--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
    "--add-opens=java.base/sun.net.dns=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
    "--add-opens=java.base/sun.nio.fs=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.ssl=ALL-UNNAMED",
    "--add-opens=java.base/sun.security.util=ALL-UNNAMED",
    "--add-opens=java.desktop/com.sun.java.swing=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.dnd.peer=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.event=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.font=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.image=ALL-UNNAMED",
    "--add-opens=java.desktop/java.awt.peer=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.plaf.basic=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.text=ALL-UNNAMED",
    "--add-opens=java.desktop/javax.swing.text.html=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.datatransfer=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.image=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.awt.windows=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.font=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.java2d=ALL-UNNAMED",
    "--add-opens=java.desktop/sun.swing=ALL-UNNAMED",
    "--add-opens=java.management/sun.management=ALL-UNNAMED",
    "--add-opens=jdk.attach/sun.tools.attach=ALL-UNNAMED",
    "--add-opens=jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED",
    "--add-opens=jdk.internal.jvmstat/sun.jvmstat.monitor=ALL-UNNAMED",
    "--add-opens=jdk.jdi/com.sun.tools.jdi=ALL-UNNAMED"
)

$fullArgs = $vmArgs + $addOpens + @("-cp", $cpString, "com.intellij.idea.Main")

Write-Host "[INFO] Launching Jörmungandr process ($binName)..." -ForegroundColor Gray
Write-Host "[INFO] Process will appear in Task Manager under: $binName" -ForegroundColor Gray

if ($Console) {
    & $javaExe @fullArgs
} else {
    $proc = Start-Process -FilePath $javaExe -ArgumentList $fullArgs -PassThru
    Write-Host "[OK]   Jörmungandr launched successfully! (PID: $($proc.Id))" -ForegroundColor Green
}
