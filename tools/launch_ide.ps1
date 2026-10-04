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

$nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
$acceptedStr = "rsch.send.usage.stat:1.1:0:$nowMs;eap:2021.2:0:$nowMs;`n"
$cachedJson = '[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Surveys","printableName":"Feedback","accepted":"false"}]'

# Pre-populate in sandbox config paths
@(
    (Join-Path $configDir "consentOptions"),
    (Join-Path $configDir "jormungandr\consentOptions"),
    (Join-Path $configDir "idea\consentOptions")
) | ForEach-Object {
    if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
    Set-Content -Path (Join-Path $_ "accepted") -Value $acceptedStr -Encoding UTF8
    Set-Content -Path (Join-Path $_ "cached") -Value $cachedJson -Encoding UTF8
}

# Pre-populate in User AppData paths for both custom vendor and JetBrains fallback
if ($env:APPDATA) {
    @(
        (Join-Path $env:APPDATA "indoctrinatedrecluse\consentOptions"),
        (Join-Path $env:APPDATA "indoctrinatedrecluse\jormungandr\consentOptions"),
        (Join-Path $env:APPDATA "indoctrinatedrecluse\idea\consentOptions"),
        (Join-Path $env:APPDATA "JetBrains\consentOptions"),
        (Join-Path $env:APPDATA "JetBrains\jormungandr\consentOptions"),
        (Join-Path $env:APPDATA "JetBrains\idea\consentOptions")
    ) | ForEach-Object {
        try {
            if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
            Set-Content -Path (Join-Path $_ "accepted") -Value $acceptedStr -Encoding UTF8
            Set-Content -Path (Join-Path $_ "cached") -Value $cachedJson -Encoding UTF8
        } catch {}
    }
}

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
$resourcesDir = Join-Path $ProjectRoot "modules\platform-shell\src\main\resources"
$cpString = "$resourcesDir;" + ($libJars -join ";")

$vmArgs = @(
    "-Xms256m",
    "-Xmx2048m",
    "-XX:ReservedCodeCacheSize=512m",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:-OmitStackTraceInFastThrow",
    "-XX:CICompilerCount=2",
    "-Didea.vendor.name=indoctrinatedrecluse",
    "-Didea.product.name=Jörmungandr",
    "-Didea.application.name=Jörmungandr",
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
    # Ensure Win32 helper is loaded to target interactive desktop and activate window
    if (-not ([System.Management.Automation.PSTypeName]'JormNativeLauncher').Type) {
        Add-Type @"
using System;
using System.Text;
using System.Runtime.InteropServices;
using System.Diagnostics;

public class JormNativeLauncher {
    [StructLayout(LayoutKind.Sequential, CharSet = CharSet.Unicode)]
    public struct STARTUPINFO {
        public int cb;
        public string lpReserved;
        public string lpDesktop;
        public string lpTitle;
        public int dwX;
        public int dwY;
        public int dwXSize;
        public int dwYSize;
        public int dwXCountChars;
        public int dwYCountChars;
        public int dwFillAttribute;
        public int dwFlags;
        public short wShowWindow;
        public short cbReserved2;
        public IntPtr lpReserved2;
        public IntPtr hStdInput;
        public IntPtr hStdOutput;
        public IntPtr hStdError;
    }

    [StructLayout(LayoutKind.Sequential)]
    public struct PROCESS_INFORMATION {
        public IntPtr hProcess;
        public IntPtr hThread;
        public int dwProcessId;
        public int dwThreadId;
    }

    public delegate bool EnumWindowsProc(IntPtr hWnd, IntPtr lParam);

    [DllImport("kernel32.dll", SetLastError = true, CharSet = CharSet.Unicode)]
    public static extern bool CreateProcess(
        string lpApplicationName,
        string lpCommandLine,
        IntPtr lpProcessAttributes,
        IntPtr lpThreadAttributes,
        bool bInheritHandles,
        uint dwCreationFlags,
        IntPtr lpEnvironment,
        string lpCurrentDirectory,
        ref STARTUPINFO lpStartupInfo,
        out PROCESS_INFORMATION lpProcessInformation
    );

    [DllImport("kernel32.dll", SetLastError = true)]
    public static extern bool CloseHandle(IntPtr hObject);

    [DllImport("user32.dll")]
    public static extern bool SetForegroundWindow(IntPtr hWnd);

    [DllImport("user32.dll")]
    public static extern bool ShowWindowAsync(IntPtr hWnd, int nCmdShow);

    [DllImport("user32.dll")]
    public static extern void SwitchToThisWindow(IntPtr hWnd, bool fAltTab);

    [DllImport("user32.dll")]
    public static extern bool AllowSetForegroundWindow(int dwProcessId);

    [DllImport("user32.dll")]
    public static extern bool EnumWindows(EnumWindowsProc enumProc, IntPtr lParam);

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint lpdwProcessId);

    [DllImport("user32.dll")]
    public static extern bool IsWindowVisible(IntPtr hWnd);

    public const int SW_RESTORE = 9;
    public const int ASFW_ANY = -1;

    public static int StartOnInteractiveDesktop(string appPath, string cmdLine, string workingDir) {
        STARTUPINFO si = new STARTUPINFO();
        si.cb = Marshal.SizeOf(si);
        si.lpDesktop = @"WinSta0\Default";
        si.dwFlags = 1; // STARTF_USESHOWWINDOW
        si.wShowWindow = 1; // SW_SHOWNORMAL

        PROCESS_INFORMATION pi = new PROCESS_INFORMATION();
        string fullCmd = "\"" + appPath + "\" " + cmdLine;
        bool success = CreateProcess(null, fullCmd, IntPtr.Zero, IntPtr.Zero, false, 0, IntPtr.Zero, workingDir, ref si, out pi);
        if (success) {
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
            return pi.dwProcessId;
        }
        return -Marshal.GetLastWin32Error();
    }

    public static IntPtr FindWindowForProcess(int pid) {
        IntPtr found = IntPtr.Zero;
        EnumWindows((hWnd, lParam) => {
            uint procId;
            GetWindowThreadProcessId(hWnd, out procId);
            if (procId == pid && IsWindowVisible(hWnd)) {
                found = hWnd;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return found;
    }

    public static void ActivateWindow(IntPtr hWnd) {
        AllowSetForegroundWindow(ASFW_ANY);
        ShowWindowAsync(hWnd, SW_RESTORE);
        SetForegroundWindow(hWnd);
        SwitchToThisWindow(hWnd, true);
    }
}
"@
    }

    $quotedArgs = $fullArgs | ForEach-Object {
        if ($_ -match '\s' -and -not ($_ -match '^".*"$')) { "`"$_`"" } else { $_ }
    }
    $argString = $quotedArgs -join ' '

    $launchedPid = [JormNativeLauncher]::StartOnInteractiveDesktop($javaExe, $argString, $ProjectRoot)
    if ($launchedPid -gt 0) {
        Write-Host "[OK]   Jörmungandr launched on interactive desktop! (PID: $launchedPid)" -ForegroundColor Green
    } else {
        Write-Host "[WARN] Direct desktop assignment returned ($launchedPid); launching via Start-Process..." -ForegroundColor Yellow
        $proc = Start-Process -FilePath $javaExe -ArgumentList $fullArgs -PassThru
        $launchedPid = $proc.Id
        Write-Host "[OK]   Jörmungandr launched successfully! (PID: $launchedPid)" -ForegroundColor Green
    }

    Write-Host "[INFO] Monitoring IDE startup to ensure foreground focus..." -ForegroundColor Gray
    $timeout = [DateTime]::UtcNow.AddSeconds(15)
    $activated = $false
    while ([DateTime]::UtcNow -lt $timeout) {
        Start-Sleep -Milliseconds 500
        $p = Get-Process -Id $launchedPid -ErrorAction SilentlyContinue
        if (-not $p -or $p.HasExited) { break }
        $hwnd = $p.MainWindowHandle
        if ($hwnd -eq [IntPtr]::Zero) {
            $hwnd = [JormNativeLauncher]::FindWindowForProcess($launchedPid)
        }
        if ($hwnd -ne [IntPtr]::Zero) {
            [JormNativeLauncher]::ActivateWindow($hwnd)
            $activated = $true
            Write-Host "[OK]   Jörmungandr window activated and focused on display!" -ForegroundColor Green
            break
        }
    }
}
