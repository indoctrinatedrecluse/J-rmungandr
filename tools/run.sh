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
else
    info "Target task: $TASK"
    TASKS+=("$TASK")
fi

info "Executing: ./gradlew ${TASKS[*]} ${EXTRA_ARGS[*]:-}"
./gradlew "${TASKS[@]}" "${EXTRA_ARGS[@]:-}"
