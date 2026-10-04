#!/usr/bin/env bash
#
# Jörmungandr - IDE Launch & Dependency Verification Script (Linux / macOS)
#
# Verifies build prerequisites (Java 21 LTS, Gradle wrapper, Python),
# auto-configures/repairs missing dependencies where possible, and builds/runs the IDE.
#

set -euo pipefail

# ---------------------------------------------------------------------------
# Path & Directory Resolution
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Resolve project root from tools/ directory
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# ---------------------------------------------------------------------------
# Visual Formatting Helpers
# ---------------------------------------------------------------------------
if [[ -t 1 ]]; then
    RED='\033[0;31m'
    GREEN='\033[0;32m'
    YELLOW='\033[1;33m'
    CYAN='\033[0;36m'
    MAGENTA='\033[0;35m'
    NC='\033[0m'
else
    RED=''
    GREEN=''
    YELLOW=''
    CYAN=''
    MAGENTA=''
    NC=''
fi

info()  { echo -e "${CYAN}[INFO]${NC}  $*"; }
ok()    { echo -e "${GREEN}[OK]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
fixed() { echo -e "${MAGENTA}[FIXED]${NC} $*"; }
fail()  { echo -e "${RED}[FAIL]${NC}  $*"; }

header() {
    echo -e "\n${CYAN}============================================================${NC}"
    echo -e "  $*"
    echo -e "${CYAN}============================================================${NC}"
}

# ---------------------------------------------------------------------------
# Argument Parsing
# ---------------------------------------------------------------------------
TASK=":modules:platform-shell:runIde"
CLEAN=0
BUILD_ONLY=0
RUN_ONLY=0
RUN_TESTS=0
SKIP_CHECK=0
EXTRA_ARGS=()

show_help() {
    header "🐍 Jörmungandr - IDE Runner (Linux / macOS)"
    echo "Usage: ./tools/run.sh [OPTIONS] [GRADLE_ARGS...]"
    echo ""
    echo "Options:"
    echo "  -c, --clean        Cleans build artifacts before running"
    echo "  -b, --build-only   Assembles plugin artifacts without launching the IDE"
    echo "  -r, --run-only     Runs existing built target binary without rebuilding"
    echo "  -t, --test         Runs all automated test suites"
    echo "      --skip-check   Bypasses environment dependency verification"
    echo "      --task <name>  Custom Gradle task (default: :modules:platform-shell:runIde)"
    echo "  -h, --help         Displays this help message"
    exit 0
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -c|--clean)
            CLEAN=1
            shift
            ;;
        -b|--build-only)
            BUILD_ONLY=1
            shift
            ;;
        -r|--run-only|-RunOnly)
            RUN_ONLY=1
            shift
            ;;
        -t|--test)
            RUN_TESTS=1
            shift
            ;;
        --skip-check)
            SKIP_CHECK=1
            shift
            ;;
        --task)
            TASK="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            ;;
        *)
            EXTRA_ARGS+=("$1")
            shift
            ;;
    esac
done

header "🐍 Jörmungandr - IDE Runner & Environment Verifier"
info "Project Root: $PROJECT_ROOT"

# ---------------------------------------------------------------------------
# Dependency Verification & Self-Healing
# ---------------------------------------------------------------------------
if [[ $SKIP_CHECK -eq 0 ]]; then
    echo -e "\n--- Checking Build & Runtime Prerequisites ---"

    TARGET_JAVA_MAJOR="21"
    VALID_JDK_HOME=""

    check_jdk() {
        local candidate="$1"
        if [[ -z "$candidate" || ! -d "$candidate" ]]; then
            return 1
        fi
        local java_bin="$candidate/bin/java"
        if [[ ! -x "$java_bin" ]]; then
            return 1
        fi

        # Check release file first for instant version parsing
        if [[ -f "$candidate/release" ]]; then
            if grep -qE '^JAVA_VERSION="?21\.' "$candidate/release" 2>/dev/null; then
                echo "$candidate"
                return 0
            fi
        fi

        # Fallback to invoking java -version
        local ver_output
        ver_output="$("$java_bin" -version 2>&1 || true)"
        if echo "$ver_output" | grep -qE 'version "(21\.|21")'; then
            echo "$candidate"
            return 0
        fi

        return 1
    }

    # 1. Check existing JAVA_HOME
    if [[ -n "${JAVA_HOME:-}" ]]; then
        if valid=$(check_jdk "$JAVA_HOME"); then
            VALID_JDK_HOME="$valid"
            ok "Found valid Java 21 in JAVA_HOME: $VALID_JDK_HOME"
        else
            warn "Current JAVA_HOME ($JAVA_HOME) is not Java $TARGET_JAVA_MAJOR. Searching for a compatible JDK..."
        fi
    fi

    # 2. Search common Unix JDK paths if JAVA_HOME is missing or invalid
    if [[ -z "$VALID_JDK_HOME" ]]; then
        CANDIDATES=(
            "/usr/lib/jvm/java-21-openjdk-amd64"
            "/usr/lib/jvm/java-21-openjdk-arm64"
            "/usr/lib/jvm/java-21-openjdk"
            "/usr/lib/jvm/temurin-21-jdk"
            "/usr/lib/jvm/default-java"
            "/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home"
            "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home"
            "/opt/homebrew/opt/openjdk@21"
            "/usr/local/opt/openjdk@21"
            "$HOME/.sdkman/candidates/java/21"*
            "$HOME/.asdf/installs/java/temurin-21"*
            "$HOME/.jdks/jdk-21"*
        )

        for pattern in "${CANDIDATES[@]}"; do
            for dir in $pattern; do
                if valid=$(check_jdk "$dir"); then
                    VALID_JDK_HOME="$valid"
                    break 2
                fi
            done
        done

        # 3. Check java on current PATH
        if [[ -z "$VALID_JDK_HOME" ]] && command -v java >/dev/null 2>&1; then
            REAL_JAVA=$(readlink -f "$(which java)" 2>/dev/null || which java)
            PARENT_DIR="$(dirname "$(dirname "$REAL_JAVA")")"
            if valid=$(check_jdk "$PARENT_DIR"); then
                VALID_JDK_HOME="$valid"
            fi
        fi

        if [[ -n "$VALID_JDK_HOME" ]]; then
            fixed "Configured JAVA_HOME to Java 21: $VALID_JDK_HOME"
            export JAVA_HOME="$VALID_JDK_HOME"
            export PATH="$JAVA_HOME/bin:$PATH"
        fi
    fi

    # Terminate if no Java 21 found
    if [[ -z "$VALID_JDK_HOME" ]]; then
        fail "Java 21 LTS is required to build and run Jörmungandr, but was not found."
        echo ""
        echo -e "${YELLOW}Please install OpenJDK 21 using your package manager:${NC}"
        echo "  - Debian/Ubuntu: sudo apt-get install openjdk-21-jdk"
        echo "  - Fedora/RHEL:   sudo dnf install java-21-openjdk-devel"
        echo "  - Arch Linux:    sudo pacman -S jdk21-openjdk"
        echo "  - macOS:         brew install openjdk@21"
        echo "  - SDKMAN!:       sdk install java 21.0.5-tem"
        echo "Then re-run this script."
        exit 1
    fi

    # 4. Gradle Wrapper Verification & Permission Self-Healing
    GRADLEW="$PROJECT_ROOT/gradlew"
    WRAPPER_JAR="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.jar"
    WRAPPER_PROPS="$PROJECT_ROOT/gradle/wrapper/gradle-wrapper.properties"

    if [[ ! -f "$GRADLEW" ]]; then
        fail "Missing gradlew script in $PROJECT_ROOT."
        exit 1
    fi

    if [[ ! -x "$GRADLEW" ]]; then
        chmod +x "$GRADLEW" 2>/dev/null || true
        fixed "Set executable permissions on $GRADLEW (+x)."
    fi

    if [[ ! -f "$WRAPPER_JAR" || ! -f "$WRAPPER_PROPS" ]]; then
        warn "Gradle wrapper files missing or incomplete in gradle/wrapper/."
        if command -v gradle >/dev/null 2>&1; then
            info "Regenerating Gradle wrapper using system gradle..."
            (cd "$PROJECT_ROOT" && gradle wrapper)
            fixed "Regenerated Gradle wrapper successfully."
        else
            fail "Gradle wrapper files missing and no system 'gradle' command was found."
            echo -e "${YELLOW}Please install Gradle or restore gradle/wrapper/gradle-wrapper.jar.${NC}"
            exit 1
        fi
    else
        ok "Gradle wrapper verified."
    fi

    # 5. Python Environment Check (Informational)
    if command -v python3 >/dev/null 2>&1; then
        PY_VER="$(python3 --version 2>&1)"
        ok "Python environment detected: $PY_VER ($(which python3))"
    elif command -v python >/dev/null 2>&1; then
        PY_VER="$(python --version 2>&1)"
        ok "Python environment detected: $PY_VER ($(which python))"
    else
        warn "Python 3 was not detected on PATH."
        warn "Jörmungandr data science features require Python 3.10+."
    fi

    # 6. Git Check (Informational)
    if command -v git >/dev/null 2>&1; then
        ok "Git detected: $(git --version)"
    else
        warn "Git command not found on PATH."
    fi
fi

# ---------------------------------------------------------------------------
# Argument Conflict Validation
# ---------------------------------------------------------------------------
if [[ $RUN_ONLY -eq 1 && $BUILD_ONLY -eq 1 ]]; then
    fail "Conflicting arguments: -RunOnly and -BuildOnly cannot be used together."
    exit 1
fi

if [[ $RUN_ONLY -eq 1 && $RUN_TESTS -eq 1 ]]; then
    fail "Conflicting arguments: -RunOnly and -Test cannot be used together."
    exit 1
fi

if [[ $RUN_ONLY -eq 1 && $CLEAN -eq 1 ]]; then
    warn "-Clean cannot be used with -RunOnly as it would remove the target binary. Ignoring -Clean."
    CLEAN=0
fi

# ---------------------------------------------------------------------------
# Target Binary Verification (RunOnly Mode)
# ---------------------------------------------------------------------------
if [[ $RUN_ONLY -eq 1 ]]; then
    echo -e "\n--- Checking Built Target Binary ---"
    TARGET_LIBS_DIR="$PROJECT_ROOT/modules/platform-shell/build/libs"
    TARGET_DIST_DIR="$PROJECT_ROOT/modules/platform-shell/build/distributions"
    TARGET_BINARY=""

    if compgen -G "$TARGET_LIBS_DIR/platform-shell-*.jar" > /dev/null 2>&1; then
        for f in "$TARGET_LIBS_DIR"/platform-shell-*.jar; do
            if [[ "$f" != *"-base.jar" && "$f" != *"-instrumented.jar" && "$f" != *"-searchableOptions.jar" ]]; then
                TARGET_BINARY="$f"
                break
            fi
        done
    fi

    if [[ -z "$TARGET_BINARY" ]] && compgen -G "$TARGET_DIST_DIR/platform-shell-*.zip" > /dev/null 2>&1; then
        TARGET_BINARY="$(ls -1 "$TARGET_DIST_DIR"/platform-shell-*.zip 2>/dev/null | head -n 1)"
    fi

    if [[ -z "$TARGET_BINARY" ]]; then
        fail "No existing built target binary found in '$TARGET_LIBS_DIR' or '$TARGET_DIST_DIR'."
        echo -e "${YELLOW}Please build the project first (e.g., './tools/run.sh -b' or './tools/run.sh') before using -RunOnly.${NC}"
        exit 1
    fi

    ok "Found existing built target binary: $TARGET_BINARY"
fi

# ---------------------------------------------------------------------------
# Sandbox EULA & First-Run Initialization
# ---------------------------------------------------------------------------
init_sandbox_eula() {
    local project_root="$1"
    info "Initializing sandbox EULA & first-run configuration..."

    local platform_ver="2024.3.2"
    local props_file="$project_root/gradle.properties"
    if [[ -f "$props_file" ]]; then
        local matched
        matched=$(grep -E '^platformVersion\s*=' "$props_file" | head -n 1 | cut -d'=' -f2 | tr -d '[:space:]' || true)
        if [[ -n "$matched" ]]; then
            platform_ver="$matched"
        fi
    fi

    local now_ms
    now_ms=$(date +%s%3N 2>/dev/null || echo "1790799343000")

    local sandbox_dirs=(
        "$project_root/modules/platform-shell/build/idea-sandbox/IC-$platform_ver/config"
    )

    local sandbox_base="$project_root/modules/platform-shell/build/idea-sandbox"
    if [[ -d "$sandbox_base" ]]; then
        for ic_dir in "$sandbox_base"/IC-*/; do
            if [[ -d "$ic_dir" ]]; then
                sandbox_dirs+=("${ic_dir}config")
            fi
        done
    fi

    for cfg in "${sandbox_dirs[@]}"; do
        mkdir -p "$cfg/consentOptions" "$cfg/options"
        echo "rsch.send.usage.stat:1.1:0:$now_ms" > "$cfg/consentOptions/accepted"

        local other_xml="$cfg/options/other.xml"
        if [[ -f "$other_xml" ]]; then
            if ! grep -q "eua_accepted_version" "$other_xml"; then
                sed -i 's/"keyToString": {/"keyToString": {\n    "eua_accepted_version": "2.0",\n    "privacy_policy_accepted_version": "2.0",\n    "previous_eua_accepted_version": "2.0",\n    "ask.about.tip.of.the.day": "false",\n    "show.tips.on.startup": "false",/' "$other_xml" 2>/dev/null || true
            fi
        else
            cat << 'EOF' > "$other_xml"
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
EOF
        fi

        local general_local="$cfg/options/ide.general.local.xml"
        if [[ ! -f "$general_local" ]]; then
            cat << 'EOF' > "$general_local"
<application>
  <component name="GeneralLocalSettings">
    <option name="showTipsOnStartup" value="false" />
  </component>
</application>
EOF
        fi
    done

    mkdir -p "$HOME/.config/JetBrains/consentOptions" 2>/dev/null || true
    echo "rsch.send.usage.stat:1.1:0:$now_ms" > "$HOME/.config/JetBrains/consentOptions/accepted" 2>/dev/null || true
    ok "Sandbox EULA & first-run configuration initialized."
}

if [[ $BUILD_ONLY -eq 0 && $RUN_TESTS -eq 0 && "$TASK" == *"runIde"* ]]; then
    init_sandbox_eula "$PROJECT_ROOT"
fi

# ---------------------------------------------------------------------------
# Build & Execution
# ---------------------------------------------------------------------------
echo -e "\n--- Executing Gradle ---"
cd "$PROJECT_ROOT"

TASKS=()
if [[ $CLEAN -eq 1 ]]; then
    info "Running build clean..."
    TASKS+=("clean")
fi

if [[ $RUN_TESTS -eq 1 ]]; then
    info "Target task: test (running all module test suites)"
    TASKS+=("test")
elif [[ $BUILD_ONLY -eq 1 ]]; then
    info "Target task: :modules:platform-shell:buildPlugin (build-only mode)"
    TASKS+=(":modules:platform-shell:buildPlugin")
elif [[ $RUN_ONLY -eq 1 ]]; then
    info "Target task: $TASK (run-only mode: skipping compilation and rebuild tasks)"
    TASKS+=("$TASK" "-x" "compileKotlin" "-x" "compileJava" "-x" "instrumentCode" "-x" "jar")
else
    info "Target task: $TASK"
    TASKS+=("$TASK")
fi

if [[ $BUILD_ONLY -eq 0 && $RUN_TESTS -eq 0 && "$TASK" == *"runIde"* ]]; then
    TASKS+=(
        "-Djb.consents.confirmation.enabled=false"
        "-Deua.consents.confirmation.enabled=false"
        "-Didea.initially.ask.config=false"
        "-Dide.show.tips.on.startup=false"
    )
fi

info "Executing: ./gradlew ${TASKS[*]} ${EXTRA_ARGS[*]:-}"
./gradlew "${TASKS[@]}" "${EXTRA_ARGS[@]:-}"

