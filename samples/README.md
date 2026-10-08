# 🧪 Jörmungandr Testing & Evaluation Samples

This directory provides pre-configured test artifacts, datasets, notebooks, SQL queries, dbt models, Airflow DAGs, and ML checkpoints to verify and evaluate all modular suites and extensions in Jörmungandr.

---

## 📁 Directory Layout

```
samples/
├── README.md                                 # This testing and evaluation guide
├── 01_notebooks/                             # Reactive Jupyter Notebook test cases
│   ├── 01_reactive_dag_demo.ipynb            # Variable dependency tracking & topological execution
│   ├── 02_rich_outputs_and_math.ipynb        # LaTeX math, collapsible JSON trees, HTML formatting
│   ├── 03_audio_video_multimedia.ipynb       # Java Sound waveform card & video player launcher
│   └── 04_data_science_workflow.ipynb        # End-to-end Pandas, DuckDB in-memory queries & stats
├── 02_datasets/                              # Tabular data & distribution drift testing
│   ├── generate_datasets.py                  # Python generator script for synthetic datasets
│   ├── customers.csv                         # 250 customer rows for Data Grid & correlation matrix
│   ├── financial_transactions.csv            # 500 rows for financial telemetry & window functions
│   ├── sensor_telemetry.json                 # 150 streaming IoT packets for Oscilloscope testing
│   ├── reference_train.csv                   # Baseline reference training distribution
│   └── current_production.csv                # Drifted production distribution (tests PSI & K-S tests)
├── 03_lakehouse_and_sql/                     # Database & Lakehouse testing
│   ├── duckdb_lakehouse_queries.sql          # Analytical DuckDB queries over CSV/Parquet
│   ├── postgres_mysql_schema.sql             # Relational DDL & DML for Database Suite console
│   └── mock_delta_table/                     # Delta Lake ACID table with _delta_log commits
│       ├── _delta_log/
│       │   ├── 00000000000000000000.json     # Initial CREATE commit
│       │   ├── 00000000000000000001.json     # OPTIMIZE z-order commit
│       │   └── 00000000000000000002.json     # MERGE upsert commit
├── 04_pipeline_orchestration/                # dbt & Airflow DAG Lineage Studio
│   ├── dbt_analytics_project/                # dbt project structure
│   │   ├── dbt_project.yml                   # Project manifest
│   │   └── models/
│   │       ├── staging/                      # Staging views (stg_customers, stg_orders)
│   │       └── marts/                        # Mart tables (fct_daily_revenue, dim_customer_churn)
│   └── airflow_dags/                         # Apache Airflow DAGs
│       └── customer_etl_dag.py               # Airflow DAG with task dependency operators
├── 05_machine_learning/                      # AI/ML Studio & Model Inspection
│   ├── train_model_and_shap.py               # Model evaluation & SHAP waterfall calculations
│   ├── ml_evaluation_report.json             # Model metrics & feature attribution output
│   ├── resnet50_sample.safetensors           # Binary Safetensors checkpoint for Neural Graph Inspector
│   ├── generate_mock_checkpoints.py          # Safetensors mock binary generator
│   └── sample_model_metadata.json            # Metadata for 1-Click model deployment packaging
└── 06_r_statistical/                         # R REPL & ggplot2 Visual Interception
    └── ggplot2_statistical_analysis.R        # Observational cohort study & ggplot2 visual test
```

---

## 🎯 How to Test Each Feature Suite

### 1. 🪐 Reactive Jupyter Notebook Subsystem
- **Open**: `samples/01_notebooks/01_reactive_dag_demo.ipynb`.
- **Test Actions**:
  - Run Cells 1, 2, and 3.
  - Open the **Reactive DAG Tool Window** to view variable lineage.
  - Modify `base_scalar` in Cell 1 without executing Cell 2.
  - Verify that downstream cells display the `⚠️ Stale` warning badge.
  - Click **Topological Run** to verify Kahn's topological sort execution order.
- **Open**: `samples/01_notebooks/02_rich_outputs_and_math.ipynb`.
  - Verify inline LaTeX formulas ($E=mc^2$) and display math blocks render cleanly.
  - Interact with the JSON tree: expand nodes and copy JSON paths.
- **Open**: `samples/01_notebooks/03_audio_video_multimedia.ipynb`.
  - Verify the audio waveform card visualizer and SVG rendering.

### 2. 📊 Tabular DataFrame Viewer & Data Prep Studio
- **Open**: `samples/02_datasets/customers.csv`.
  - Verify virtualized grid rendering, sortable column headers, and inline text filters (`country:US`).
  - Open **Data Quality Studio**: verify completeness scorecards and IQR outlier boundary calculations.
  - Open **Correlation Heatmap**: toggle between Pearson, Spearman, and Kendall Tau matrices.
- **Test Distribution Drift**:
  - Open `samples/02_datasets/reference_train.csv` and `samples/02_datasets/current_production.csv`.
  - In the Drift Studio, select `reference_train.csv` as Reference and `current_production.csv` as Current.
  - Verify the calculation of the **Population Stability Index (PSI)** and **Kolmogorov-Smirnov (K-S)** tests, triggering `🔴 SEVERE DRIFT` on `endpoint_latency_ms`.

### 3. 🗄️ Database Suite & In-Memory DuckDB Lakehouse
- **Open**: `samples/03_lakehouse_and_sql/duckdb_lakehouse_queries.sql`.
  - Execute queries directly against local CSV files without starting an external SQL server.
  - Verify Window functions (`ROWS BETWEEN 5 PRECEDING AND CURRENT ROW`) and ranking calculations.
- **Open**: `samples/03_lakehouse_and_sql/mock_delta_table/`.
  - In the Lakehouse Studio tool window, point the catalog path to `mock_delta_table`.
  - Verify that the ACID timeline parses the 3 historical commits (`CREATE`, `OPTIMIZE`, `MERGE`).
  - Test the **Time-Travel SQL Generator** (`VERSION AS OF 1` / `TIMESTAMP AS OF`).

### 4. 🔄 Pipeline & Lineage Studio (dbt & Airflow)
- **Open**: The `samples/04_pipeline_orchestration/` folder.
  - Open the **Pipeline Studio** tool window.
  - Click **Scan Lineage**.
  - Verify the 2D interactive canvas renders the dependency DAG:
    - `stg_customers` & `stg_orders` $\rightarrow$ `fct_daily_revenue` & `dim_customer_churn`.
    - `customer_etl_dag.py` operators connected with cubic-bezier arrows.
  - Test cursor-anchored mouse wheel zoom, click-and-drag panning, and double-click jump to SQL/Python source code.

### 5. 🧠 AI/ML Studio & Model Checkpoint Inspector
- **Inspect Checkpoint**:
  - Right-click `samples/05_machine_learning/resnet50_sample.safetensors` $\rightarrow$ **Inspect Model Checkpoint**.
  - Verify that layer names, tensor precisions (`F32`, `F16`), shapes (`[64, 3, 7, 7]`), and byte offsets are dissected into the Neural Graph.
- **Inspect Explainability**:
  - Run `samples/05_machine_learning/train_model_and_shap.py` to generate `ml_evaluation_report.json`.
  - Verify the SHAP waterfall feature attribution force plot values.

### 6. 🔮 R Language & Statistical REPL
- **Open**: `samples/06_r_statistical/ggplot2_statistical_analysis.R`.
  - Send lines or selections to the R REPL (`Ctrl+Enter` / `Alt+Shift+E`).
  - Verify linear regression model summaries print to console and plot output is captured into the Scientific Plot Viewer.
