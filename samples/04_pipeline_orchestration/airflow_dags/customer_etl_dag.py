"""
Apache Airflow DAG Lineage Sample
Tests Jörmungandr's Pipeline Studio parser and interactive cubic-bezier DAG visualizer.
"""

from datetime import datetime, timedelta

try:
    from airflow import DAG
    from airflow.operators.python import PythonOperator
    from airflow.operators.bash import BashOperator
except ImportError:
    # Lightweight pure-Python mock classes for running without apache-airflow installed
    class _MockTask:
        def __init__(self, task_id, **kwargs):
            self.task_id = task_id
            self.kwargs = kwargs
            self.downstream = []

        def __rshift__(self, other):
            if isinstance(other, _MockTask):
                self.downstream.append(other)
            return other

        def __lshift__(self, other):
            if isinstance(other, _MockTask):
                other.downstream.append(self)
            return other

        def execute(self):
            fn = self.kwargs.get('python_callable')
            if callable(fn):
                return fn()
            cmd = self.kwargs.get('bash_command')
            if cmd:
                print(f"[BashOperator] {cmd}")

    class DAG:
        def __init__(self, dag_id, **kwargs):
            self.dag_id = dag_id
            self.kwargs = kwargs
            self.tasks = []

        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc_val, exc_tb):
            pass

    class PythonOperator(_MockTask):
        pass

    class BashOperator(_MockTask):
        pass

default_args = {
    'owner': 'data_platform',
    'depends_on_past': False,
    'start_date': datetime(2026, 1, 1),
    'email_on_failure': True,
    'retries': 2,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    dag_id='lakehouse_customer_hourly_sync',
    default_args=default_args,
    description='Extracts transactional streams, validates data quality, and synchronizes with Delta Lake',
    schedule_interval='@hourly',
    catchup=False,
    tags=['lakehouse', 'streaming', 'delta_lake', 'jormungandr']
) as dag:

    def extract_raw_events():
        print("Extracting batch window from Kafka / JSON stream...")

    def validate_quality_scorecard():
        print("Computing Population Stability Index and Pandera checks...")

    def transform_duckdb_marts():
        print("Transforming in-memory DuckDB analytical marts...")

    def optimize_delta_acid_table():
        print("Executing VACUUM and OPTIMIZE z-order on Delta Lake...")

    task_extract = PythonOperator(
        task_id='extract_raw_events',
        python_callable=extract_raw_events
    )

    task_validate = PythonOperator(
        task_id='validate_quality_scorecard',
        python_callable=validate_quality_scorecard
    )

    task_transform = PythonOperator(
        task_id='transform_duckdb_marts',
        python_callable=transform_duckdb_marts
    )

    task_optimize = PythonOperator(
        task_id='optimize_delta_acid_table',
        python_callable=optimize_delta_acid_table
    )

    task_notify = BashOperator(
        task_id='notify_slack_pipeline_success',
        bash_command='echo "Pipeline completed successfully"'
    )

    # Topological execution dependencies:
    # task_extract -> task_validate -> task_transform -> task_optimize -> task_notify
    task_extract >> task_validate >> task_transform >> task_optimize >> task_notify

    try:
        import jormungandr as jm
        jm.show_pipeline_lineage(dag_id="lakehouse_customer_hourly_sync")
    except Exception:
        pass

