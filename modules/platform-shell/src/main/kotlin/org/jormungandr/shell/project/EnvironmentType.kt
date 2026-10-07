package org.jormungandr.shell.project

import java.io.File

/**
 * Result of environment provisioning.
 */
data class ProvisionResult(
    val success: Boolean,
    val message: String,
    val venvPath: File? = null
)

/**
 * Supported Python environment managers for bootstrapping new data science workspaces.
 */
enum class EnvironmentType(
    val id: String,
    val displayName: String,
    val description: String,
    val commandHint: String
) {
    UV(
        id = "uv",
        displayName = "⚡ uv (High-Speed Rust Python Package Manager)",
        description = "Ultra-fast package resolution and virtual environment creation via Astrid/uv.",
        commandHint = "uv venv && uv pip install -r requirements.txt"
    ),

    VENV(
        id = "venv",
        displayName = "🐍 Standard Python venv (Built-in)",
        description = "Native Python 3 standard library virtual environment (.venv).",
        commandHint = "python -m venv .venv && pip install -r requirements.txt"
    ),

    CONDA(
        id = "conda",
        displayName = "🪐 Conda (Anaconda / Miniconda / Mamba)",
        description = "Isolated conda environment with environment.yml configuration.",
        commandHint = "conda env create -f environment.yml"
    ),

    POETRY(
        id = "poetry",
        displayName = "📜 Poetry (Deterministic Lockfiles)",
        description = "Python dependency management and packaging using pyproject.toml.",
        commandHint = "poetry install"
    ),

    MANUAL(
        id = "manual",
        displayName = "📋 Manual / System Environment",
        description = "Do not auto-create an isolated environment; use active system Python.",
        commandHint = "pip install -r requirements.txt"
    );

    /**
     * Generates auxiliary environment configuration and bootstrap scripts for the target directory.
     */
    fun generateEnvironmentFiles(projectName: String, dependencies: List<String>): List<TemplateFile> {
        val files = mutableListOf<TemplateFile>()

        when (this) {
            UV -> {
                files.add(TemplateFile("tools/setup_env.ps1", generateUvPowerShellScript()))
                files.add(TemplateFile("tools/setup_env.sh", generateUvBashScript()))
                files.add(TemplateFile("tools/setup_env.bat", generateUvBatScript()))
            }

            VENV -> {
                files.add(TemplateFile("tools/setup_env.ps1", generateVenvPowerShellScript()))
                files.add(TemplateFile("tools/setup_env.sh", generateVenvBashScript()))
                files.add(TemplateFile("tools/setup_env.bat", generateVenvBatScript()))
            }

            CONDA -> {
                files.add(TemplateFile("environment.yml", generateCondaEnvironmentYaml(projectName, dependencies)))
                files.add(TemplateFile("tools/setup_env.ps1", generateCondaPowerShellScript(projectName)))
                files.add(TemplateFile("tools/setup_env.sh", generateCondaBashScript(projectName)))
            }

            POETRY -> {
                files.add(TemplateFile("tools/setup_env.ps1", "Write-Host 'Running poetry install...'\npoetry install\n"))
                files.add(TemplateFile("tools/setup_env.sh", "#!/usr/bin/env bash\necho 'Running poetry install...'\npoetry install\n"))
            }

            MANUAL -> {
                // No additional scripts needed
            }
        }

        return files
    }

    private fun generateUvPowerShellScript(): String = """
        Write-Host "⚡ Initializing high-speed Python virtual environment via uv..." -ForegroundColor Cyan
        if (-not (Get-Command uv -ErrorAction SilentlyContinue)) {
            Write-Warning "uv was not found on PATH. Falling back to python -m venv..."
            python -m venv .venv
            .\.venv\Scripts\python.exe -m pip install --upgrade pip
            .\.venv\Scripts\pip.exe install -r requirements.txt
        } else {
            uv venv .venv
            uv pip install -r requirements.txt
        }
        Write-Host "✅ Environment setup complete! Active in .venv" -ForegroundColor Green
    """.trimIndent()

    private fun generateUvBashScript(): String = """
        #!/usr/bin/env bash
        set -e
        echo "⚡ Initializing high-speed Python virtual environment via uv..."
        if ! command -v uv &> /dev/null; then
            echo "uv not found on PATH. Falling back to python3 -m venv..."
            python3 -m venv .venv
            ./.venv/bin/pip install --upgrade pip
            ./.venv/bin/pip install -r requirements.txt
        else
            uv venv .venv
            uv pip install -r requirements.txt
        fi
        echo "✅ Environment setup complete! Active in .venv"
    """.trimIndent()

    private fun generateUvBatScript(): String = """
        @echo off
        echo ⚡ Initializing environment via uv...
        where uv >nul 2>nul
        if %ERRORLEVEL% NEQ 0 (
            echo uv not found, falling back to python -m venv...
            python -m venv .venv
            call .\.venv\Scripts\activate.bat
            pip install -r requirements.txt
        ) else (
            uv venv .venv
            uv pip install -r requirements.txt
        )
        echo ✅ Done.
    """.trimIndent()

    private fun generateVenvPowerShellScript(): String = """
        Write-Host "🐍 Creating standard Python virtual environment in .venv..." -ForegroundColor Cyan
        python -m venv .venv
        Write-Host "📦 Installing dependencies from requirements.txt..." -ForegroundColor Cyan
        .\.venv\Scripts\python.exe -m pip install --upgrade pip
        .\.venv\Scripts\pip.exe install -r requirements.txt
        Write-Host "✅ Python venv initialized successfully!" -ForegroundColor Green
    """.trimIndent()

    private fun generateVenvBashScript(): String = """
        #!/usr/bin/env bash
        set -e
        echo "🐍 Creating standard Python virtual environment in .venv..."
        python3 -m venv .venv
        echo "📦 Installing dependencies from requirements.txt..."
        ./.venv/bin/pip install --upgrade pip
        ./.venv/bin/pip install -r requirements.txt
        echo "✅ Python venv initialized successfully!"
    """.trimIndent()

    private fun generateVenvBatScript(): String = """
        @echo off
        echo 🐍 Creating standard Python virtual environment in .venv...
        python -m venv .venv
        call .\.venv\Scripts\activate.bat
        pip install --upgrade pip
        pip install -r requirements.txt
        echo ✅ Done.
    """.trimIndent()

    private fun generateCondaEnvironmentYaml(projectName: String, dependencies: List<String>): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        val depsList = dependencies.joinToString("\n") { "  - $it" }
        return """
            name: $safeName
            channels:
              - conda-forge
              - defaults
            dependencies:
              - python=3.11
              - pip
            $depsList
              - pip:
                - jupyterlab
        """.trimIndent()
    }

    private fun generateCondaPowerShellScript(projectName: String): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return """
            Write-Host "🪐 Creating Conda environment '$safeName' from environment.yml..." -ForegroundColor Cyan
            conda env create -f environment.yml
            Write-Host "✅ Conda environment created! Activate with: conda activate $safeName" -ForegroundColor Green
        """.trimIndent()
    }

    private fun generateCondaBashScript(projectName: String): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9_-]"), "_")
        return """
            #!/usr/bin/env bash
            echo "🪐 Creating Conda environment '$safeName' from environment.yml..."
            conda env create -f environment.yml
            echo "✅ Conda environment created! Activate with: conda activate $safeName"
        """.trimIndent()
    }
}
