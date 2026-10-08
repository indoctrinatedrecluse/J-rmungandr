# Changelog

All notable changes to the **Jörmungandr IDE** project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [1.0.0] - 2026-10-07

### 🎉 Initial Public Release

Welcome to the inaugural release of **Jörmungandr** — the modular, open-source IDE for Python, Data Science, and Analytics built atop the IntelliJ Platform Community Edition.

---

### 🌟 Key Feature Highlights

#### 🪐 1. Reactive Jupyter Notebook Subsystem
- **Native `.ipynb` Editor**: Split code and Markdown cells with syntax highlighting, inline execution, and output virtualization.
- **ZeroMQ v5 Kernel Protocol**: Direct, zero-latency communication with local and remote Python (`ipykernel`) environments.
- **Reactive Dependency DAG & AST Analysis**:
  - Static AST analysis of variables defined and consumed across cells to automatically construct inter-cell directed acyclic execution graphs.
  - Interactive 2D topological graph canvas showing variable lineage and execution order.
  - Automatic `⚠️ Stale` badges indicating cells whose dependencies were modified out of sequence.
  - 1-click Kahn's topological sort execution to eliminate hidden state and restore notebook consistency.
- **JupyterLab Muscle Memory & Command Mode**: Full keyboard navigation (`Esc`, `Enter`, `A`/`B` to insert, `DD` to delete, `M`/`Y` cell type toggles, `J`/`K` navigation, `Shift+Enter` and `Ctrl+Enter` execution).
- **Rich Media & Formula Typography**:
  - LaTeX mathematical typography for `$ ... $`, `$$ ... $$`, and `text/latex` outputs.
  - Interactive collapsible JSON tree inspector with search filtering and path copying.
  - Native audio player card with waveform visualizer and Java Sound playback engine.
  - Native video preview player card with system player launch integration.

#### 📊 2. High-Performance DataFrame Viewer & Data Prep Studio
- **Tabular Grid Explorer**: Virtualized tabular view capable of rendering millions of rows with instant scrolling, column reordering, sorting, and inline filtering (`col:val` queries).
- **Column Analytics**: Per-column distribution histograms, min/mean/max indicators, null count telemetry, and IQR metrics.
- **Automated Data Quality & Correlation Studio**:
  - Multi-metric correlation heatmaps supporting Pearson, Spearman Rank, and Kendall Tau coefficients.
  - Data Quality Scorecard tracking completeness, uniqueness, and column health.
  - Automated IQR/Z-score outlier detection and Pandera schema rule verification.
- **Distribution Drift & Dataset Comparison Studio**:
  - Two-dataset comparison (Train vs Test / Prod vs Staging) with schema diffing.
  - Statistical drift metrics: Population Stability Index (PSI) and Two-Sample Kolmogorov-Smirnov (K-S) test with asymptotic p-value estimation.
  - Severity classification (`🟢 STABLE`, `🟡 MODERATE`, `🔴 SEVERE`) and dual histogram / Empirical CDF overlay visualizer.
- **1-Click Interactive Web App Generator**:
  - Generates standalone, production-ready **Streamlit** and **Gradio** web application scripts directly from any DataFrame or ML model.
  - Generates parameter sliders, categorical filters, metric cards, and Plotly charts.
- **In-Memory DuckDB SQL Lakehouse**: Zero-copy SQL execution directly over active live DataFrames and Parquet files without database servers.
- **Agentic "Talk to Your Data"**: Natural language query engine translating plain English questions into DuckDB SQL queries and Pandas operations.

#### 🗄️ 3. Database & Streaming Suite
- **Relational SQL Support**: Native connection managers and query consoles for PostgreSQL, MySQL, SQLite, DuckDB, Snowflake, and Oracle PL/SQL.
- **NoSQL & Document Engines**: Dedicated studios for MongoDB, Redis (keyspace & command studio), and Apache Cassandra (CQL).
- **Streaming Broker**: Apache Kafka topic inspector, message producer/consumer, and partition lag monitor.
- **Real-Time Streaming & Timeseries Studio**:
  - Sliding memory window buffer (50 to 2000 events) for high-throughput stream ingress without JVM heap bloat.
  - Simulators and live connectors for IoT Sensor Telemetry, FinTech Payments, and Kafka topics.
  - 60 FPS live animated oscilloscope canvas with continuous rolling averages.
  - *"❄️ Freeze to DataFrame"*: 1-click snapshotting of live in-flight streams into native DataFrames.
- **Remote Data Lakes**: Amazon S3 and Google Cloud Storage bucket object explorer.
- **Visual SQL Builder**: Low-code visual join diagram and drag-and-drop query generator.

#### 🧠 4. AI/ML Training, Explainability & Checkpoint Studio
- **Interactive ML Training Studio**:
  - Interactive training for Linear/Ridge Regression, Logistic Classification (with ROC/AUC curves and confusion matrices), K-Means Clustering, and PCA Scree plots.
  - Preliminary feature categorization, distribution analysis, and health profiling on demand without training.
  - Built-in Scikit-Learn, PyTorch, and XGBoost training code templates.
- **Model Explainability & Attribution Studio (SHAP & PDP)**:
  - Local attribution force plots (SHAP Waterfall) visualizing positive/negative feature contributions against base value $E[f(X)]$.
  - Global feature importance ranking bar charts.
  - Partial Dependence Plots (PDP) and Individual Conditional Expectation (ICE) curves.
  - 1-click Python SHAP script generator.
- **Model Checkpoint & Neural Graph Inspector**:
  - Zero-copy header inspection for `.safetensors`, `.onnx`, `.pt`, `.pth`, and `.h5` model files.
  - Tensor shapes, precisions (`F16`, `BF16`, `F32`), and memory footprint analysis.
  - Interactive neural layer feed-forward DAG visualizer.
- **Experiment Tracker & Model Packager**:
  - In-IDE MLflow-style experiment tracking with metric convergence curves.
  - 1-Click production model packager generating FastAPI microservices (`app.py`), Dockerfiles, and client test harnesses.

#### 🔮 5. R Language & Statistical REPL
- Automatic local R binary discovery (`Rscript.exe`, `R.exe`) across system PATH, `R_HOME`, and Conda.
- CRAN package inventorying (`ggplot2`, `dplyr`, `arrow`, `IRkernel`).
- Interactive REPL console with history recall (`Ctrl+Up` / `Ctrl+Down`) and simulation mode fallback.
- Automatic interception of `ggplot2` and base-R graphics into the centralized Scientific Plot Viewer.
- Interception of tabular outputs into the DataFrame Viewer.

#### 🎨 6. Platform Shell & Visual Identity
- Clean IntelliJ Community Edition 2024.3.2 base with Java 21 LTS runtime.
- Bespoke high-resolution branding, custom splash screens, and customized dark/light themes.
- Custom vector iconography (`pluginIcon.svg`) for all modular extension suites.
- Universal interactive 2D pan/zoom controller with mouse-wheel zoom and smooth canvas dragging across all charts and DAGs.
- Deterministic extension lifecycle management with memory limits and zero resource leakage.

#### 🎮 7. Hardware Telemetry & GPU Monitor Status Bar
- **Non-blocking Telemetry Daemon**: Real-time polling of GPU compute utilization, VRAM allocation (used / total MB), and thermals via `nvidia-smi` with automatic Host RAM and CPU fallback.
- **Proactive OOM Prevention Watchdog**: Live status monitoring across `STABLE`, `ELEVATED`, and `CRITICAL_OOM_RISK` tiers to safeguard long-running PyTorch/TensorFlow trainings.
- **Status Bar Widget & Interactive Telemetry Popup**: Click-to-open GPU status bar meter with color-coded gauge bars and thermal readout.
- **1-Click Memory Cache Purge**: Instantly trims JVM off-heap allocations, garbage collection roots, and native cache buffers.

#### 🤖 8. Local AI & LLM Engine (Offline Copilot & Prompt Studio)
- **Local Daemon Integration**: Zero-latency, 100% offline client for local LLM servers (**Ollama** and **vLLM** at `localhost:11434`) ensuring code and data privacy.
- **Model Discovery & Latency Telemetry**: Automatic detection of downloaded weights (`llama3`, `deepseek-coder`, `mistral`, `codellama`, `qwen`) with tokens/second throughput benchmarking.
- **Interactive Prompt Studio Tool Window**: Docked workspace with system prompt customization, temperature tuning slider, and 1-click active editor code insertion.
- **Context-Menu AI Code Actions**: Right-click actions generating Google/NumPy format docstrings and robust PyTest mock fixtures.

#### 🏛️ 9. Modern Lakehouse & Deep Parquet Inspector
- **Binary Parquet Metadata Inspector (`PAR1`)**: Header/footer inspection, row group chunk layouts, compression codec efficiency ratios (Snappy, ZSTD, GZIP, LZ4), dictionary encodings, and column min/max stats.
- **Delta Lake & Apache Iceberg ACID Timeline**: Parses `_delta_log/*.json` and `metadata/*.metadata.json` catalogs to surface snapshot history, commit operations (`WRITE`, `OPTIMIZE`, `MERGE`), and changed file manifests.
- **1-Click Time-Travel SQL Generator**: Produces ready-to-run point-in-time SQL queries for DuckDB and Apache Spark (`VERSION AS OF` / `TIMESTAMP AS OF`).

#### 🔄 10. Data Orchestration & Pipeline Lineage Visualizer (dbt & Airflow)
- **Project AST & Lineage Parser**: Scans projects for dbt SQL models (`ref`, `source`, `config`) and Apache Airflow DAGs (`DAG`, `>>`, `<<`, `task_id`).
- **Interactive 2D DAG Lineage Canvas**: High-performance Swing canvas with anti-aliased cubic bezier connecting arrows, color-coded node badges, and topological layer columns.
- **Graph Interactions**: Cursor-anchored mouse wheel zoom, pan, upstream/downstream dependency highlighting, and double-click jump to source code.
- **Lineage Actions**: 1-click execution simulation and CLI command generator (`dbt run --select`, `airflow tasks test`).

#### 📦 11. Windows Single-File Installer & System Integration
- **Inno Setup Script**: `installer/jormungandr_setup.iss` and `tools/build_installer.ps1` for generating `Jormungandr-Setup-v1.0.0-x64.exe`.
- **System File Associations**: Windows shell registration for `.ipynb` (Jupyter), `.parquet` (Parquet), and `.arrow` / `.feather` (Arrow).
- **Desktop & PATH Integration**: Start Menu and Desktop shortcuts with high-resolution Jörmungandr branding and optional CLI PATH addition.

---

### 🛠️ Build & Verification Infrastructure
- Cross-platform Gradle wrapper build configuration supporting Java 21 LTS.
- `tools/run.ps1` and `tools/run.sh` automated prerequisite verification and environment auto-healing.
- `tools/launch_ide.ps1` direct native launcher bypassing Gradle daemon overhead.
- Automated GitHub Actions release pipeline building both standalone portable distributions and modular plugin packages.
