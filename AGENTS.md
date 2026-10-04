# Jörmungandr Workspace & Agent Guidelines

## Official Project Root
The official, definitive workspace directory for this project is:
```
D:\Projects\Jörmungandr
```

## Critical Rules for Antigravity & AI Assistants
1. **Never use scratch or temporary directories**: Do NOT use `C:\Users\RECLUSE\.gemini\antigravity\scratch` or any obsolete paths. All source files, assets, build tools, and tests reside in `D:\Projects\Jörmungandr`.
2. **Working Directory (`Cwd`)**: Always execute commands, tests, and builds with the working directory set to `D:\Projects\Jörmungandr`.
3. **Run Scripts**: Always use `tools/run.ps1` (PowerShell) or `tools/run.sh` (Bash) from the project root for verification, dependency healing, test runs, and launching the IDE.
4. **Git Repository**: The repository remote is `https://github.com/indoctrinatedrecluse/Jormungandr.git`. Keep the local `main` branch synced with `origin/main`.
5. **Coding & License Standards**: Maintain Apache 2.0 attribution for `indoctrinatedrecluse (2025–2026)` and upstream JetBrains IntelliJ Platform.
