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

.PARAMETER Test
    Runs all module unit and integration tests.

.PARAMETER SkipCheck
    Bypasses dependency verification.

.EXAMPLE
    .\tools\run.ps1
    .\tools\run.ps1 -Clean
    .\tools\run.ps1 -BuildOnly
    .\tools\run.ps1 -Test
    .\tools\run.ps1 -Task ":modules:dataframe-viewer:test"
#>

[CmdletBinding()]
param(
    [string]$Task = ":modules:platform-shell:runIde",
    [switch]$Clean,
    [switch]$BuildOnly,
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
} else {
    Write-Info "Target task: $Task"
    $tasksToRun += $Task
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
