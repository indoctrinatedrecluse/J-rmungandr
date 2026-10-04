<#
.SYNOPSIS
    Jörmungandr Run & Dependency Verification Script for Windows (PowerShell).

.DESCRIPTION
    Verifies build prerequisites (Java 21 LTS, Gradle wrapper, Ant/Javac2 compatibility, Python),
    auto-configures / repairs missing dependencies where possible, and builds/runs the Jörmungandr IDE.

.PARAMETER Task
    Gradle task to execute. Defaults to ':modules:platform-shell:runIde'.

.PARAMETER Clean
    Performs a clean build before execution.

.PARAMETER BuildOnly
    Builds the IDE plugin package without starting the IDE sandbox.

.PARAMETER RunOnly
    Runs the existing built target binary without rebuilding.

.PARAMETER Test
    Runs all module unit and integration tests.

.PARAMETER SkipCheck
    Bypasses dependency verification.

.EXAMPLE
    .\tools\run.ps1
    .\tools\run.ps1 -Clean
    .\tools\run.ps1 -BuildOnly
    .\tools\run.ps1 -RunOnly
    .\tools\run.ps1 -Test
    .\tools\run.ps1 -Task ":modules:dataframe-viewer:test"
#>

[CmdletBinding()]
param(
    [string]$Task = ":modules:platform-shell:runIde",
    [switch]$Clean,
    [switch]$BuildOnly,
    [switch]$RunOnly,
    [switch]$Test,
    [switch]$SkipCheck,
    [switch]$Help,
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]]$ExtraArgs
)

Set-StrictMode -Off
$ErrorActionPreference = "Stop"

# Ensure UTF-8 output encoding for emojis and international characters
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

# ---------------------------------------------------------------------------
# Path & Directory Resolution
# ---------------------------------------------------------------------------
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
# Resolve project root from tools/ directory
$ProjectRoot = (Resolve-Path (Join-Path $ScriptDir "..")).Path

# ---------------------------------------------------------------------------
# Visual Formatting Helpers
# ---------------------------------------------------------------------------
function Write-Header {
    param([string]$Message)
    Write-Host "`n============================================================" -ForegroundColor Cyan
    Write-Host "  $Message" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
}

function Write-Info  { param([string]$Msg) Write-Host "[INFO]  $Msg" -ForegroundColor Gray }
function Write-Ok    { param([string]$Msg) Write-Host "[OK]    $Msg" -ForegroundColor Green }
function Write-Warn  { param([string]$Msg) Write-Host "[WARN]  $Msg" -ForegroundColor Yellow }
function Write-Fixed { param([string]$Msg) Write-Host "[FIXED] $Msg" -ForegroundColor Magenta }
function Write-Fail  { param([string]$Msg) Write-Host "[FAIL]  $Msg" -ForegroundColor Red }

# ---------------------------------------------------------------------------
# Help Display
# ---------------------------------------------------------------------------
if ($Help) {
    Write-Header "Jörmungandr IDE Run Script"
    Write-Host "Usage: .\tools\run.ps1 [OPTIONS] [GRADLE_ARGS...]"
    Write-Host ""
    Write-Host "Options:"
    Write-Host "  -Clean        Cleans build artifacts before running"
    Write-Host "  -BuildOnly    Assembles plugin artifacts without launching the IDE"
    Write-Host "  -RunOnly      Runs existing built target binary without rebuilding"
    Write-Host "  -Test         Runs all automated test suites"
    Write-Host "  -SkipCheck    Bypasses environment dependency verification"
    Write-Host "  -Task <name>  Custom Gradle task (default: :modules:platform-shell:runIde)"
    Write-Host "  -Help         Displays this help message"
    exit 0
}

Write-Header "🐍 Jörmungandr - IDE Runner & Environment Verifier"
Write-Info "Project Root: $ProjectRoot"

# ---------------------------------------------------------------------------
# Dependency Verification & Self-Healing
# ---------------------------------------------------------------------------
if (-not $SkipCheck) {
    Write-Host "`n--- Checking Build & Runtime Prerequisites ---" -ForegroundColor White

    # 1. Java 21 Check & Auto-Configuration
    $targetJavaMajor = 21
    $validJdkHome = $null

    function Test-JdkPath {
        param([string]$Path)
        if (-not (Test-Path $Path)) { return $null }

        $javaBin = Join-Path $Path "bin\java.exe"
        if (-not (Test-Path $javaBin)) { return $null }

        # Check release file first for instant version parsing
        $releaseFile = Join-Path $Path "release"
        if (Test-Path $releaseFile) {
            $verLine = Get-Content $releaseFile | Where-Object { $_ -match '^JAVA_VERSION="?([0-9]+)' } | Select-Object -First 1
            if ($verLine -and ($Matches[1] -eq "$targetJavaMajor")) {
                return $Path
            }
        }

        # Fallback: invoke java -version
        try {
            $verOut = (& $javaBin -version 2>&1) -join "`n"
            if ($verOut -match 'version "([0-9]+)') {
                if ([int]$Matches[1] -eq $targetJavaMajor) {
                    return $Path
                }
            }
        } catch {}

        return $null
    }

    # A. Check existing JAVA_HOME
    if ($env:JAVA_HOME) {
        $validJdkHome = Test-JdkPath $env:JAVA_HOME
        if ($validJdkHome) {
            Write-Ok "Found valid Java 21 in JAVA_HOME: $validJdkHome"
        } else {
            Write-Warn "Current JAVA_HOME ($env:JAVA_HOME) is not Java $targetJavaMajor. Searching for a compatible JDK..."
        }
    }

    # B. Search known candidate paths if JAVA_HOME is missing or invalid
    if (-not $validJdkHome) {
        $candidateDirs = @(
            "$env:USERPROFILE\.jdks\jdk-21*",
            "C:\Program Files\Java\jdk-21*",
            "C:\Program Files\Eclipse Adoptium\jdk-21*",
            "C:\Program Files\Android\openjdk\jdk-21*",
            "C:\Program Files\BellSoft\LibericaJDK-21*",
            "C:\Program Files\Amazon Corretto\jdk21*",
            "C:\Program Files\Microsoft\jdk-21*",
            "$env:LOCALAPPDATA\Programs\Eclipse Adoptium\jdk-21*"
        )

        foreach ($pattern in $candidateDirs) {
            $matched = Get-Item -Path $pattern -ErrorAction SilentlyContinue
            foreach ($dir in $matched) {
                $tested = Test-JdkPath $dir.FullName
                if ($tested) {
                    $validJdkHome = $tested
                    break
                }
            }
            if ($validJdkHome) { break }
        }

        # C. Check java.exe in current PATH
        if (-not $validJdkHome) {
            $pathJava = Get-Command java -ErrorAction SilentlyContinue
            if ($pathJava) {
                $parentDir = Split-Path (Split-Path $pathJava.Source -Parent) -Parent
                $validJdkHome = Test-JdkPath $parentDir
            }
        }

        if ($validJdkHome) {
            Write-Fixed "Configured JAVA_HOME to Java 21 installation: $validJdkHome"
            $env:JAVA_HOME = $validJdkHome
            $env:PATH = "$validJdkHome\bin;$env:PATH"
        }
    }

    # D. Terminate if no Java 21 found
    if (-not $validJdkHome) {
        Write-Fail "Java 21 LTS is required to build and run Jörmungandr, but was not found."
        Write-Host ""
        Write-Host "Please install OpenJDK 21 using one of the following methods:" -ForegroundColor Yellow
        Write-Host "  - Winget:     winget install EclipseAdoptium.Temurin.21.JDK"
        Write-Host "  - Chocolatey: choco install openjdk21"
        Write-Host "  - Manual:     https://adoptium.net/temurin/releases/?version=21"
        Write-Host "Then either set JAVA_HOME or re-run this script."
        exit 1
    }

    # 2. Windows Ant / Javac2 Quirk Self-Healing
    # The IntelliJ Platform Gradle Plugin's Ant Javac2 instrumenter checks for $JAVA_HOME\Packages on Windows
    $packagesDir = Join-Path $validJdkHome "Packages"
    if (-not (Test-Path $packagesDir)) {
        try {
            New-Item -ItemType Directory -Path $packagesDir -Force -ErrorAction SilentlyContinue | Out-Null
            Write-Fixed "Created '$packagesDir' to prevent Ant/Javac2 bytecode instrumenter failure."
        } catch {
            Write-Warn "Could not create '$packagesDir' (permission restricted). If build fails on Javac2, run PowerShell as Administrator once."
        }
    } else {
        Write-Ok "Ant Javac2 compatibility verified ($packagesDir exists)."
    }

    # 3. Gradle Wrapper Verification
    $gradlewBat = Join-Path $ProjectRoot "gradlew.bat"
    $wrapperJar = Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.jar"
    $wrapperProps = Join-Path $ProjectRoot "gradle\wrapper\gradle-wrapper.properties"

    if (-not (Test-Path $gradlewBat)) {
        Write-Fail "Missing gradlew.bat in $ProjectRoot."
        exit 1
    }

    if (-not (Test-Path $wrapperJar) -or -not (Test-Path $wrapperProps)) {
        Write-Warn "Gradle wrapper files missing or incomplete in gradle/wrapper/."
        $sysGradle = Get-Command gradle -ErrorAction SilentlyContinue
        if ($sysGradle) {
            Write-Info "Attempting repair using system Gradle ($($sysGradle.Source))..."
            Push-Location $ProjectRoot
            try {
                & gradle wrapper
                Write-Fixed "Regenerated Gradle wrapper files successfully."
            } catch {
                Write-Fail "Failed to regenerate wrapper with system Gradle."
                exit 1
            } finally {
                Pop-Location
            }
        } else {
            Write-Fail "Gradle wrapper jar is missing and no system 'gradle' command was found."
            Write-Host "Please install Gradle or restore gradle/wrapper/gradle-wrapper.jar from the repository." -ForegroundColor Yellow
            exit 1
        }
    } else {
        Write-Ok "Gradle wrapper verified."
    }

    # 4. Python Environment Check (Informational)
    $pythonCmd = Get-Command python -ErrorAction SilentlyContinue
    if (-not $pythonCmd) {
        $pythonCmd = Get-Command py -ErrorAction SilentlyContinue
    }

    if ($pythonCmd) {
        try {
            $pyVer = (& $pythonCmd.Source --version 2>&1) -join " "
            Write-Ok "Python environment detected: $pyVer ($($pythonCmd.Source))"
        } catch {
            Write-Ok "Python executable found: $($pythonCmd.Source)"
        }
    } else {
        Write-Warn "Python 3 was not detected on PATH."
        Write-Warn "Jörmungandr data science features (Jupyter, DataFrame viewer, virtual environments) require Python 3.10+."
    }

    # 5. Git Check (Informational)
    $gitCmd = Get-Command git -ErrorAction SilentlyContinue
    if ($gitCmd) {
        Write-Ok "Git detected."
    } else {
        Write-Warn "Git not found on PATH."
    }
}

# ---------------------------------------------------------------------------
# Argument Conflict Validation
# ---------------------------------------------------------------------------
if ($RunOnly -and $BuildOnly) {
    Write-Fail "Conflicting arguments: -RunOnly and -BuildOnly cannot be used together."
    exit 1
}
if ($RunOnly -and $Test) {
    Write-Fail "Conflicting arguments: -RunOnly and -Test cannot be used together."
    exit 1
}
if ($RunOnly -and $Clean) {
    Write-Warn "-Clean cannot be used with -RunOnly as it would remove the target binary. Ignoring -Clean."
    $Clean = $false
}

# ---------------------------------------------------------------------------
# Target Binary Verification (RunOnly Mode)
# ---------------------------------------------------------------------------
if ($RunOnly) {
    Write-Host "`n--- Checking Built Target Binary ---" -ForegroundColor White
    $targetLibsDir = Join-Path $ProjectRoot "modules\platform-shell\build\libs"
    $targetDistDir = Join-Path $ProjectRoot "modules\platform-shell\build\distributions"

    $targetJar = Get-ChildItem -Path $targetLibsDir -Filter "platform-shell-*.jar" -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notmatch "-(base|instrumented|searchableOptions)\.jar$" } |
        Select-Object -First 1

    if (-not $targetJar) {
        $targetJar = Get-ChildItem -Path $targetDistDir -Filter "platform-shell-*.zip" -ErrorAction SilentlyContinue |
            Select-Object -First 1
    }

    if (-not $targetJar) {
        Write-Fail "No existing built target binary found in '$targetLibsDir' or '$targetDistDir'."
        Write-Host "Please build the project first (e.g., '.\tools\run.ps1 -BuildOnly' or '.\tools\run.ps1') before using -RunOnly." -ForegroundColor Yellow
        exit 1
    }

    Write-Ok "Found existing built target binary: $($targetJar.FullName) ($([math]::Round($targetJar.Length / 1MB, 2)) MB)"
}

# ---------------------------------------------------------------------------
# Sandbox EULA & First-Run Initialization
# ---------------------------------------------------------------------------
function Initialize-SandboxEula {
    param([string]$ProjectRoot)

    Write-Host "`n--- Initializing Sandbox EULA & First-Run Configuration ---" -ForegroundColor White

    $platformVer = "2024.3.2"
    $propsFile = Join-Path $ProjectRoot "gradle.properties"
    if (Test-Path $propsFile) {
        $verLine = Get-Content $propsFile | Where-Object { $_ -match '^platformVersion\s*=\s*(.+)$' } | Select-Object -First 1
        if ($verLine) {
            $platformVer = $Matches[1].Trim()
        }
    }

    $sandboxDirs = @(
        Join-Path $ProjectRoot "modules\platform-shell\build\idea-sandbox\IC-$platformVer\config"
    )

    $ideaSandboxRoot = Join-Path $ProjectRoot "modules\platform-shell\build\idea-sandbox"
    if (Test-Path $ideaSandboxRoot) {
        Get-ChildItem -Path $ideaSandboxRoot -Directory -Filter "IC-*" -ErrorAction SilentlyContinue | ForEach-Object {
            $cfg = Join-Path $_.FullName "config"
            if ($sandboxDirs -notcontains $cfg) {
                $sandboxDirs += $cfg
            }
        }
    }

    $nowMs = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    $acceptedStr = "rsch.send.usage.stat:1.1:0:$nowMs;eap:2021.2:0:$nowMs;`n"
    $cachedJson = '[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Surveys","printableName":"Feedback","accepted":"false"}]'

    foreach ($cfgDir in $sandboxDirs) {
        try {
            @(
                (Join-Path $cfgDir "consentOptions"),
                (Join-Path $cfgDir "jormungandr\consentOptions"),
                (Join-Path $cfgDir "idea\consentOptions")
            ) | ForEach-Object {
                if (-not (Test-Path $_)) { New-Item -ItemType Directory -Path $_ -Force | Out-Null }
                Set-Content -Path (Join-Path $_ "accepted") -Value $acceptedStr -Encoding UTF8
                Set-Content -Path (Join-Path $_ "cached") -Value $cachedJson -Encoding UTF8
            }
            Write-Ok "Pre-configured consent options in: $cfgDir"

            $optionsDir = Join-Path $cfgDir "options"
            if (-not (Test-Path $optionsDir)) {
                New-Item -ItemType Directory -Path $optionsDir -Force | Out-Null
            }

            $otherXmlFile = Join-Path $optionsDir "other.xml"
            if (Test-Path $otherXmlFile) {
                $xmlContent = Get-Content -Raw -Path $otherXmlFile
                if (-not ($xmlContent -match "eua_accepted_version")) {
                    $xmlContent = $xmlContent -replace '"keyToString": \{', @"
"keyToString": {
    "eua_accepted_version": "2.0",
    "privacy_policy_accepted_version": "2.0",
    "previous_eua_accepted_version": "2.0",
    "ask.about.tip.of.the.day": "false",
    "show.tips.on.startup": "false",
"@
                    Set-Content -Path $otherXmlFile -Value $xmlContent -Encoding UTF8
                    Write-Ok "Updated EUA properties in: $otherXmlFile"
                }
            } else {
                $otherXmlContent = @"
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
                Set-Content -Path $otherXmlFile -Value $otherXmlContent -Encoding UTF8
                Write-Ok "Created EUA configuration in: $otherXmlFile"
            }

            $generalLocalFile = Join-Path $optionsDir "ide.general.local.xml"
            if (-not (Test-Path $generalLocalFile)) {
                $generalLocalContent = @"
<application>
  <component name="GeneralLocalSettings">
    <option name="showTipsOnStartup" value="false" />
  </component>
</application>
"@
                Set-Content -Path $generalLocalFile -Value $generalLocalContent -Encoding UTF8
                Write-Ok "Created startup tips configuration in: $generalLocalFile"
            }
        } catch {
            Write-Warn "Could not write sandbox EULA in $($cfgDir): $_"
        }
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

    # 1. Jörmungandr Dedicated Product & Vendor Registry Keys
    $jormRegPaths = @(
        "HKCU:\SOFTWARE\JavaSoft\Prefs\jormungandr",
        "HKCU:\SOFTWARE\JavaSoft\Prefs\jormungandr\privacy_policy",
        "HKCU:\SOFTWARE\JavaSoft\Prefs\indoctrinatedrecluse\jormungandr",
        "HKCU:\SOFTWARE\Jormungandr"
    )
    foreach ($path in $jormRegPaths) {
        try {
            if (-not (Test-Path $path)) {
                New-Item -Path $path -Force | Out-Null
            }
            Set-ItemProperty -Path $path -Name "eua_accepted_version" -Value "2.0" -Force
            Set-ItemProperty -Path $path -Name "privacy_policy_accepted_version" -Value "2.0" -Force
            Set-ItemProperty -Path $path -Name "vendor" -Value "indoctrinatedrecluse" -Force
            Set-ItemProperty -Path $path -Name "version" -Value "0.1.0-SNAPSHOT" -Force
        } catch {
            Write-Warn "Could not write Jörmungandr registry key $path (non-fatal): $_"
        }
    }
    Write-Ok "Configured dedicated Jörmungandr product registry keys."

    # 2. Upstream IntelliJ Platform Compatibility Bridge
    try {
        $regPath = "HKCU:\SOFTWARE\JavaSoft\Prefs\jetbrains\privacy_policy"
        if (-not (Test-Path $regPath)) {
            New-Item -Path $regPath -Force | Out-Null
        }
        Set-ItemProperty -Path $regPath -Name "eua_accepted_version" -Value "2.0" -Force
        Write-Ok "Verified upstream IntelliJ Platform EUA acceptance bridge."
    } catch {
        Write-Warn "Could not write Windows Registry EUA key (non-fatal): $_"
    }
}

if (-not $BuildOnly -and -not $Test -and ($Task -match "runIde")) {
    Initialize-SandboxEula -ProjectRoot $ProjectRoot
}

# ---------------------------------------------------------------------------
# Build & Execution
# ---------------------------------------------------------------------------
Write-Host "`n--- Executing Gradle ---" -ForegroundColor White

$gradleBatPath = Join-Path $ProjectRoot "gradlew.bat"
$tasksToRun = @()

if ($Clean) {
    Write-Info "Running build clean..."
    $tasksToRun += "clean"
}

if ($Test) {
    Write-Info "Target task: test (running all module test suites)"
    $tasksToRun += "test"
} elseif ($BuildOnly) {
    Write-Info "Target task: :modules:platform-shell:buildPlugin (build-only mode)"
    $tasksToRun += ":modules:platform-shell:buildPlugin"
} elseif ($RunOnly) {
    # Verify existing built target binary exists
    $targetPlugin = Get-ChildItem -Path (Join-Path $ProjectRoot "modules\platform-shell\build\idea-sandbox\*\plugins\platform-shell\lib\platform-shell-*.jar") -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $targetPlugin) {
        Write-Fail "No existing built target binary found in modules\platform-shell\build\idea-sandbox\."
        Write-Info "Please run '.\tools\run.ps1' (without -RunOnly) first to build Jörmungandr."
        exit 1
    }
    Write-Ok "Found existing built target: $($targetPlugin.FullName)"

    if ($Task -eq ":modules:platform-shell:runIde") {
        Write-Info "Launching existing binary directly via native launcher (launch_ide.ps1)..."
        & (Join-Path $ScriptDir "launch_ide.ps1") @ExtraArgs
        exit $LASTEXITCODE
    }

    Write-Info "Target task: $Task (run-only mode: skipping compilation and rebuild tasks)"
    $tasksToRun += $Task
    $tasksToRun += @(
        "-x", "compileKotlin",
        "-x", "compileJava",
        "-x", "instrumentCode",
        "-x", "jar"
    )
} else {
    Write-Info "Target task: $Task"
    $tasksToRun += $Task
}

if (-not $BuildOnly -and -not $Test -and ($Task -match "runIde")) {
    $tasksToRun += @(
        "--no-daemon",
        "-Didea.vendor.name=indoctrinatedrecluse",
        "-Didea.product.name=Jörmungandr",
        "-Didea.application.name=Jörmungandr",
        "-Djb.consents.confirmation.enabled=false",
        "-Deua.consents.confirmation.enabled=false",
        "-Didea.initially.ask.config=false",
        "-Dide.show.tips.on.startup=false",
        "-Dwsl.use.remote.agent.for.nio.filesystem=false",
        "-Dwsl.enabled=false",
        "-Dide.ijent.wsldefault=false",
        "-Didea.wsl.support.enabled=false"
    )
    Write-Info "Starting Jörmungandr IDE..."
    Write-Info "NOTE: While the IDE is open, Gradle runs interactively (showing progress at ~95%). Gradle will finish once you close the IDE."
}

$allArgs = $tasksToRun + $ExtraArgs

Push-Location $ProjectRoot
try {
    Write-Info "Executing: .\gradlew.bat $($allArgs -join ' ')"
    & $gradleBatPath @allArgs
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        Write-Fail "Execution failed with exit code $exitCode."
        exit $exitCode
    } else {
        Write-Ok "Execution completed successfully."
    }
} finally {
    Pop-Location
}
