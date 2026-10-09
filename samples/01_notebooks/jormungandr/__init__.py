"""
Jörmungandr Data Science & Analytics IDE Python SDK
===================================================

Provides seamless programmatic triggers to launch and control Jörmungandr studios,
visualizers, lakehouse inspectors, pipeline lineage DAGs, and hardware monitors directly from code.

Example Usage:
--------------
    import jormungandr as jm

    # Launch 2D Vector Charting & DataFrame Studio
    jm.show_dataframe("samples/02_datasets/customers.csv")

    # Launch Lakehouse & Deep Parquet Inspector
    jm.show_lakehouse("samples/03_lakehouse_and_sql/mock_delta_table")

    # Launch Pipeline Lineage DAG (dbt & Airflow)
    jm.show_pipeline_lineage("samples/04_pipeline_orchestration")

    # Launch Database Analytics Studio & SQL Console
    jm.show_database_studio()

    # Launch Neural Checkpoint Inspector
    jm.show_model_inspector("samples/05_machine_learning/resnet50_sample.safetensors")

    # Launch Hardware Telemetry & GPU Monitor
    jm.show_gpu_monitor()

    # Launch Local AI & Prompt Studio
    jm.show_prompt_studio("Generate test fixtures")
"""

import os
import sys
import json

__version__ = "1.0.0"

def _trigger(action: str, **kwargs):
    payload = {"action": action, **kwargs}
    token = f"__JG_IDE_ACTION__{json.dumps(payload)}__JG_IDE_ACTION_END__"
    # Emit token to stdout for Jörmungandr kernel interception
    print(token, flush=True)
    # Also log user-friendly console status
    title = kwargs.get("title") or action
    target = kwargs.get("path") or ""
    desc = f" [{target}]" if target else ""
    try:
        print(f"[Jormungandr Studio Trigger] {title}{desc} -> Studio activated.", flush=True)
    except Exception:
        pass

def show_dataframe(data=None, path=None, title="Dataset Explorer"):
    """
    Launch Jörmungandr's flagship DataFrame Viewer & 2D Vector Charting Studio.
    Accepts:
      - A file path (CSV, TSV, Parquet)
      - A Pandas or Polars DataFrame
      - A list of dicts / records
    """
    if path is not None:
        abs_p = os.path.abspath(str(path))
        _trigger("show_dataframe", path=abs_p, title=title)
        return

    if data is not None:
        if hasattr(data, "to_dict"):
            try:
                records = data.head(500).to_dict(orient="records")
                cols = list(data.columns)
                _trigger("show_dataframe", data={"columns": cols, "records": records}, title=title)
                return
            except Exception:
                pass
        if isinstance(data, list) and len(data) > 0 and isinstance(data[0], dict):
            cols = list(data[0].keys())
            _trigger("show_dataframe", data={"columns": cols, "records": data[:500]}, title=title)
            return
        if isinstance(data, str) and os.path.exists(data):
            _trigger("show_dataframe", path=os.path.abspath(data), title=title)
            return

    _trigger("show_dataframe", title=title)

def show_lakehouse(path=None, **kwargs):
    """
    Launch the Modern Lakehouse & Deep Parquet Inspector and DuckDB Lakehouse Studio.
    Inspects binary Parquet chunks, Snappy/ZSTD compression ratios, and Delta/Iceberg time-travel.
    """
    p = os.path.abspath(str(path)) if path else None
    _trigger("show_lakehouse", path=p, title="Lakehouse & Parquet Inspector", **kwargs)

def show_pipeline_lineage(path=None, dag_id=None, **kwargs):
    """
    Launch the Data Pipelines & Lineage DAG Visualizer (dbt models and Apache Airflow DAGs).
    Renders interactive 2D cubic-bezier dependency graphs with upstream/downstream highlighting.
    """
    p = os.path.abspath(str(path)) if path and os.path.exists(str(path)) else (path or dag_id)
    _trigger("show_pipeline_lineage", path=p, dag_id=dag_id, title="Data Pipelines & Lineage DAG", **kwargs)

def show_database_studio(connection=None, dialect=None, query=None, **kwargs):
    """
    Launch the Database Analytics Studio & Multi-Dialect SQL Console.
    Connect to DuckDB, SQLite, PostgreSQL, MySQL, and Snowflake.
    """
    conn = connection or dialect
    _trigger("show_database_studio", dialect=conn, query=query, title="Database Analytics Studio", **kwargs)

def show_model_inspector(path=None, **kwargs):
    """
    Launch the AI/ML Neural Checkpoint & Architecture Inspector.
    Zero-copy header inspection for .safetensors, .onnx, .pt, .pth, and .h5 files.
    """
    p = os.path.abspath(str(path)) if path else None
    _trigger("show_model_inspector", path=p, title="Neural Checkpoint Inspector", **kwargs)

def show_gpu_monitor(**kwargs):
    """
    Trigger the Hardware Telemetry & GPU Monitor.
    Displays live GPU utilization, VRAM allocation, and thermal status.
    """
    _trigger("show_gpu_monitor", title="Hardware Telemetry & GPU Monitor", **kwargs)

def show_prompt_studio(prompt=None, model=None, system_prompt=None, **kwargs):
    """
    Launch the Local AI & Prompt Engineering Studio (Ollama / vLLM Copilot).
    """
    _trigger("show_prompt_studio", prompt=prompt, model=model, system_prompt=system_prompt, title="Local AI Prompt Studio", **kwargs)

def show_r_console(**kwargs):
    """
    Launch the R Interactive REPL & Statistical Console.
    """
    _trigger("show_r_console", title="R Interactive REPL Console", **kwargs)

def show_plots(**kwargs):
    """
    Bring the Scientific Plots & Vector Graphics Studio into view.
    """
    _trigger("show_plots", title="Scientific Plots Studio", **kwargs)

def show_dag(**kwargs):
    """
    Open the Reactive Execution DAG & Variable Dependency Graph for the active notebook.
    """
    _trigger("show_dag", title="Reactive Execution DAG", **kwargs)

