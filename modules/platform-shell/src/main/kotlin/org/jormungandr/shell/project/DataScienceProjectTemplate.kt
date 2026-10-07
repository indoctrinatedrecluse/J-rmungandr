package org.jormungandr.shell.project

import org.jormungandr.jupyter.format.NotebookFormat
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel

/**
 * File descriptor representing a file to scaffold within a new Data Science project.
 */
data class TemplateFile(
    val relativePath: String,
    val content: String,
    val isPrimaryToOpen: Boolean = false
)

/**
 * Curated Data Science & AI/ML project templates.
 */
enum class DataScienceProjectTemplate(
    val id: String,
    val displayName: String,
    val category: String,
    val iconEmoji: String,
    val summary: String,
    val description: String,
    val coreLibraries: List<String>,
    val defaultDependencies: List<String>
) {
    MACHINE_LEARNING(
        id = "machine-learning",
        displayName = "Machine Learning & Deep Learning",
        category = "Predictive Modeling",
        iconEmoji = "🧪",
        summary = "PyTorch, Scikit-Learn, LightGBM & Experiment Tracking",
        description = "Production-ready machine learning workspace with exploratory data analysis notebooks, training scripts, evaluation metrics, and PyTorch dataset definitions.",
        coreLibraries = listOf("torch", "scikit-learn", "pandas", "numpy", "matplotlib", "seaborn", "tqdm"),
        defaultDependencies = listOf(
            "torch>=2.2.0",
            "scikit-learn>=1.4.0",
            "pandas>=2.2.0",
            "numpy>=1.26.0",
            "matplotlib>=3.8.0",
            "seaborn>=0.13.0",
            "tqdm>=4.66.0",
            "joblib>=1.3.0"
        )
    ),

    TABULAR_OLAP(
        id = "tabular-olap",
        displayName = "Modern Tabular Analytics & OLAP",
        category = "Data Engineering & Analytics",
        iconEmoji = "📊",
        summary = "DuckDB, Polars, Apache Arrow & High-Speed In-Memory SQL",
        description = "High-performance analytical workspace leveraging in-process DuckDB columnar execution, Polars vector processing, and analytical SQL scripts over CSV and Parquet data lakes.",
        coreLibraries = listOf("duckdb", "polars", "pyarrow", "pandas", "matplotlib"),
        defaultDependencies = listOf(
            "duckdb>=0.10.0",
            "polars>=0.20.0",
            "pyarrow>=15.0.0",
            "pandas>=2.2.0",
            "matplotlib>=3.8.0"
        )
    ),

    GENAI_LLM(
        id = "genai-llm",
        displayName = "Generative AI & LLM Studio",
        category = "AI Agents & Language Models",
        iconEmoji = "🤖",
        summary = "Google Gemini 2.5, Multimodal Agents & RAG Vector Search",
        description = "Modern AI application template for building Gemini-powered conversational agents, retrieval-augmented generation (RAG) vector pipelines, and structured multimodal workflows.",
        coreLibraries = listOf("google-genai", "langchain", "numpy", "pydantic", "python-dotenv"),
        defaultDependencies = listOf(
            "google-genai>=0.1.0",
            "langchain>=0.2.0",
            "pydantic>=2.7.0",
            "numpy>=1.26.0",
            "python-dotenv>=1.0.0"
        )
    ),

    JUPYTER_EXPLORATORY(
        id = "jupyter-exploratory",
        displayName = "Exploratory Notebook Workspace",
        category = "Interactive Analytics",
        iconEmoji = "🪐",
        summary = "Interactive Notebooks, Visualizations & Scientific Plots",
        description = "Clean, distraction-free Jupyter exploratory workspace with interactive visualization notebooks, sample benchmark datasets, and reusable utility scripts.",
        coreLibraries = listOf("jupyter", "ipykernel", "pandas", "numpy", "matplotlib", "seaborn", "plotly"),
        defaultDependencies = listOf(
            "jupyter>=1.0.0",
            "ipykernel>=6.29.0",
            "pandas>=2.2.0",
            "numpy>=1.26.0",
            "matplotlib>=3.8.0",
            "seaborn>=0.13.0",
            "plotly>=5.20.0"
        )
    ),

    COMPUTER_VISION(
        id = "computer-vision",
        displayName = "Computer Vision & Visual Analytics",
        category = "Deep Learning & Vision",
        iconEmoji = "👁️",
        summary = "PyTorch Vision, OpenCV, Augmentations & CNN/ViT Backbones",
        description = "End-to-end computer vision workspace pre-configured with image transformation pipelines, Albumentations augmentations, and modular PyTorch backbone architectures.",
        coreLibraries = listOf("torch", "torchvision", "opencv-python", "pillow", "albumentations", "matplotlib"),
        defaultDependencies = listOf(
            "torch>=2.2.0",
            "torchvision>=0.17.0",
            "opencv-python>=4.9.0",
            "pillow>=10.2.0",
            "albumentations>=1.4.0",
            "matplotlib>=3.8.0"
        )
    );

    /**
     * Generates all template files for this project template given the project name.
     */
    fun generateFiles(projectName: String): List<TemplateFile> {
        val files = mutableListOf<TemplateFile>()

        // 1. Common Files
        files.add(TemplateFile(".gitignore", generateGitIgnore()))
        files.add(TemplateFile("pyproject.toml", generatePyProjectToml(projectName)))
        files.add(TemplateFile("requirements.txt", generateRequirementsTxt()))
        files.add(TemplateFile("README.md", generateReadme(projectName)))

        // 2. Template-Specific Files
        when (this) {
            MACHINE_LEARNING -> {
                files.add(TemplateFile("notebooks/01_exploratory_analysis.ipynb", generateMlEdaNotebook(projectName), isPrimaryToOpen = true))
                files.add(TemplateFile("notebooks/02_model_training.ipynb", generateMlTrainingNotebook(projectName)))
                files.add(TemplateFile("src/dataset.py", generateMlDatasetPy()))
                files.add(TemplateFile("src/train.py", generateMlTrainPy()))
                files.add(TemplateFile("src/evaluate.py", generateMlEvaluatePy()))
                files.add(TemplateFile("data/raw/.gitkeep", ""))
                files.add(TemplateFile("data/processed/.gitkeep", ""))
                files.add(TemplateFile("models/checkpoints/.gitkeep", ""))
            }

            TABULAR_OLAP -> {
                files.add(TemplateFile("notebooks/01_duckdb_analytics.ipynb", generateOlapNotebook(projectName), isPrimaryToOpen = true))
                files.add(TemplateFile("data/sample_sales.csv", generateSampleSalesCsv()))
                files.add(TemplateFile("queries/retention_analysis.sql", generateOlapRetentionSql()))
                files.add(TemplateFile("queries/window_aggregation.sql", generateOlapAggregationSql()))
                files.add(TemplateFile("src/etl/pipeline.py", generateOlapPipelinePy()))
                files.add(TemplateFile("data/parquet/.gitkeep", ""))
            }

            GENAI_LLM -> {
                files.add(TemplateFile("notebooks/01_gemini_multimodal.ipynb", generateGenAiNotebook(projectName), isPrimaryToOpen = true))
                files.add(TemplateFile("src/gemini_agent.py", generateGeminiAgentPy()))
                files.add(TemplateFile("src/vector_store.py", generateVectorStorePy()))
                files.add(TemplateFile("prompts/system_instructions.md", generateSystemInstructionsMd()))
                files.add(TemplateFile(".env.example", "GEMINI_API_KEY=your_gemini_api_key_here\nGEMINI_MODEL=gemini-2.5-flash\nTEMPERATURE=0.7\n"))
            }

            JUPYTER_EXPLORATORY -> {
                files.add(TemplateFile("notebooks/quickstart.ipynb", generateJupyterQuickstartNotebook(projectName), isPrimaryToOpen = true))
                files.add(TemplateFile("notebooks/data_visualization.ipynb", generateJupyterVizNotebook(projectName)))
                files.add(TemplateFile("data/iris.csv", generateSampleIrisCsv()))
                files.add(TemplateFile("src/utils.py", generateJupyterUtilsPy()))
                files.add(TemplateFile("reports/figures/.gitkeep", ""))
            }

            COMPUTER_VISION -> {
                files.add(TemplateFile("notebooks/01_image_transforms.ipynb", generateVisionNotebook(projectName), isPrimaryToOpen = true))
                files.add(TemplateFile("src/transforms.py", generateVisionTransformsPy()))
                files.add(TemplateFile("src/backbone.py", generateVisionBackbonePy()))
                files.add(TemplateFile("data/images/.gitkeep", ""))
                files.add(TemplateFile("models/weights/.gitkeep", ""))
            }
        }

        return files
    }

    private fun generateGitIgnore(): String = """
        # Byte-compiled / optimized / DLL files
        __pycache__/
        *.py[cod]
        *${'$'}py.class

        # Virtual Environments
        .venv/
        env/
        venv/
        ENV/
        .env
        .env.local

        # Jupyter Notebook Checkpoints
        .ipynb_checkpoints/

        # IDE & Editor caches
        .idea/
        .vscode/
        *.swp
        *.swo

        # Data Science Large Datasets & Checkpoint Weights
        data/raw/*
        !data/raw/.gitkeep
        data/processed/*
        !data/processed/.gitkeep
        models/checkpoints/*
        !models/checkpoints/.gitkeep
        models/weights/*
        !models/weights/.gitkeep
        *.pt
        *.pth
        *.onnx
        *.safetensors
        *.bin
        *.h5
        *.duckdb
        *.duckdb.wal

        # Distribution / Packaging
        build/
        dist/
        *.egg-info/
    """.trimIndent()

    private fun generatePyProjectToml(projectName: String): String {
        val safeName = projectName.lowercase().replace(Regex("[^a-z0-9_-]"), "-")
        val depsFormatted = defaultDependencies.joinToString(separator = "\n") { "    \"$it\"," }
        return """
            [build-system]
            requires = ["setuptools>=61.0"]
            build-backend = "setuptools.build_meta"

            [project]
            name = "$safeName"
            version = "0.1.0"
            description = "$displayName workspace created with Jörmungandr IDE"
            readme = "README.md"
            requires-python = ">=3.10"
            dependencies = [
            $depsFormatted
            ]

            [project.optional-dependencies]
            dev = [
                "pytest>=8.0.0",
                "ruff>=0.3.0",
            ]
        """.trimIndent()
    }

    private fun generateRequirementsTxt(): String = defaultDependencies.joinToString("\n") + "\n"

    private fun generateReadme(projectName: String): String = """
        # $projectName 🐍
        
        ### *$displayName*
        > Created with **Jörmungandr IDE** — The Modular Open-Source IDE for Python, Data Science & Analytics.

        ---

        ## 🚀 Getting Started

        ### 1. Environment Setup
        You can bootstrap your environment using any of the generated setup scripts:

        ```bash
        # Ultra-fast with uv (recommended)
        uv venv
        uv pip install -r requirements.txt

        # Standard Python venv
        python -m venv .venv
        source .venv/bin/activate  # On Windows: .venv\Scripts\activate
        pip install -r requirements.txt
        ```

        ### 2. Workspace Navigation
        - **Jupyter Notebooks**: Open files in `notebooks/` directly inside Jörmungandr with ZeroMQ kernel execution.
        - **Tabular Data**: Double-click datasets in `data/` to open the flagship **DataFrame Studio**.
        - **SQL & Analytics**: Execute SQL scripts in `queries/` via Jörmungandr's **Database Studio**.
        - **AI/ML Studio**: Use the **AI/ML Training Studio** tab in DataFrame Viewer for fast regression, classification, and K-Means clustering.
    """.trimIndent()

    // --- Template-Specific Generators ---

    private fun generateMlEdaNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: Exploratory Data Analysis 🧪\n\nWelcome to your machine learning exploratory workspace in Jörmungandr."
        )
        model.addCell(
            CellType.CODE,
            """
                import numpy as np
                import pandas as pd
                import matplotlib.pyplot as plt
                import seaborn as sns
                from sklearn.datasets import make_regression

                # 1. Generate synthetic regression dataset
                X, y = make_regression(n_samples=300, n_features=5, noise=12.5, random_state=42)
                feature_names = [f"feature_{i+1}" for i in range(5)]
                df = pd.DataFrame(X, columns=feature_names)
                df["target"] = y

                print(f"Dataset successfully created with {df.shape[0]} samples and {df.shape[1]} columns!")
                df.head()
            """.trimIndent()
        )
        model.addCell(
            CellType.CODE,
            """
                # 2. Inspect statistical distributions
                df.describe().round(3)
            """.trimIndent()
        )
        model.addCell(
            CellType.CODE,
            """
                # 3. Visualize feature correlation heatmap
                plt.figure(figsize=(8, 6))
                sns.heatmap(df.corr(), annot=True, cmap="coolwarm", fmt=".2f", linewidths=0.5)
                plt.title("Feature Correlation Matrix")
                plt.show()
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateMlTrainingNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: Model Training & Evaluation 🚀\n\nTrain baseline models (Ridge Regression, Random Forest, PyTorch MLP) and log evaluation metrics."
        )
        model.addCell(
            CellType.CODE,
            """
                from sklearn.model_selection import train_test_split
                from sklearn.linear_model import Ridge
                from sklearn.ensemble import RandomForestRegressor
                from sklearn.metrics import mean_squared_error, r2_score
                import matplotlib.pyplot as plt

                # Split into train/test sets
                X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

                # Train Ridge Regression
                ridge = Ridge(alpha=1.0)
                ridge.fit(X_train, y_train)
                preds = ridge.predict(X_test)

                rmse = mean_squared_error(y_test, preds, squared=False)
                r2 = r2_score(y_test, preds)

                print(f"Ridge Model Performance:")
                print(f"  • R² Score : {r2:.4f}")
                print(f"  • RMSE     : {rmse:.4f}")
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateMlDatasetPy(): String = """
        import torch
        from torch.utils.data import Dataset
        import numpy as np

        class TabularDataset(Dataset):
            '''PyTorch Dataset wrapper for tabular features and targets.'''
            def __init__(self, X: np.ndarray, y: np.ndarray):
                self.X = torch.tensor(X, dtype=torch.float32)
                self.y = torch.tensor(y, dtype=torch.float32).unsqueeze(1)

            def __len__(self) -> Int:
                return len(self.X)

            def __getitem__(self, idx: Int):
                return self.X[idx], self.y[idx]
    """.trimIndent()

    private fun generateMlTrainPy(): String = """
        import argparse
        import numpy as np
        from sklearn.datasets import make_regression
        from sklearn.model_selection import train_test_split
        from sklearn.linear_model import Ridge
        from sklearn.metrics import mean_squared_error, r2_score

        def main():
            parser = argparse.ArgumentParser(description="Train Baseline Regression Model")
            parser.add_argument("--alpha", type=float, default=1.0, help="L2 Regularization parameter")
            parser.add_argument("--samples", type=int, default=500, help="Number of samples")
            args = parser.parse_args()

            print(f"Generating {args.samples} samples and training Ridge(alpha={args.alpha})...")
            X, y = make_regression(n_samples=args.samples, n_features=5, noise=15.0, random_state=42)
            X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2, random_state=42)

            model = Ridge(alpha=args.alpha)
            model.fit(X_train, y_train)
            preds = model.predict(X_test)

            r2 = r2_score(y_test, preds)
            rmse = mean_squared_error(y_test, preds, squared=False)
            print(f"Validation Results: R²={r2:.4f}, RMSE={rmse:.4f}")

        if __name__ == "__main__":
            main()
    """.trimIndent()

    private fun generateMlEvaluatePy(): String = """
        import numpy as np
        from sklearn.metrics import mean_squared_error, mean_absolute_error, r2_score

        def evaluate_regression(y_true: np.ndarray, y_pred: np.ndarray) -> dict:
            '''Computes regression metrics.'''
            return {
                "r2": float(r2_score(y_true, y_pred)),
                "rmse": float(mean_squared_error(y_true, y_pred, squared=False)),
                "mae": float(mean_absolute_error(y_true, y_pred))
            }
    """.trimIndent()

    private fun generateOlapNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: High-Performance DuckDB & Polars Analytics 🦆\n\nPerform zero-copy SQL analytics over CSV and Parquet datasets in Jörmungandr."
        )
        model.addCell(
            CellType.CODE,
            """
                import duckdb
                import polars as pl

                con = duckdb.connect()

                # 1. Query sample sales dataset directly via DuckDB SQL
                query = '''
                    SELECT 
                        region,
                        category,
                        COUNT(*) AS total_orders,
                        ROUND(SUM(amount), 2) AS gross_revenue,
                        ROUND(AVG(amount), 2) AS avg_basket_size
                    FROM read_csv_auto('data/sample_sales.csv')
                    GROUP BY region, category
                    ORDER BY gross_revenue DESC
                '''
                df_result = con.execute(query).pl()
                print("DuckDB query executed in-process with zero overhead:")
                df_result
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateSampleSalesCsv(): String = """
        order_id,date,customer_id,region,category,amount,quantity,discount,payment_method
        1001,2026-01-05,CUST_01,North,Electronics,499.99,1,0.05,Credit Card
        1002,2026-01-07,CUST_02,West,Home Appliances,189.50,2,0.10,Debit Card
        1003,2026-01-09,CUST_03,East,Books,45.00,3,0.00,PayPal
        1004,2026-01-12,CUST_04,South,Electronics,899.00,1,0.15,Credit Card
        1005,2026-01-14,CUST_01,North,Apparel,75.25,2,0.00,Credit Card
        1006,2026-01-18,CUST_05,West,Electronics,1200.00,1,0.20,Wire Transfer
        1007,2026-01-22,CUST_06,East,Home Appliances,320.00,1,0.05,Debit Card
        1008,2026-01-25,CUST_07,South,Apparel,115.00,4,0.10,Credit Card
        1009,2026-01-29,CUST_08,North,Books,62.50,2,0.00,PayPal
        1010,2026-02-02,CUST_02,West,Electronics,749.99,1,0.05,Credit Card
        1011,2026-02-05,CUST_09,East,Apparel,210.00,3,0.15,Debit Card
        1012,2026-02-10,CUST_10,South,Home Appliances,450.00,1,0.00,Credit Card
    """.trimIndent()

    private fun generateOlapRetentionSql(): String = """
        -- Cohort Retention and Monthly Purchasing Metrics
        WITH monthly_cohorts AS (
            SELECT 
                customer_id,
                DATE_TRUNC('month', CAST(date AS DATE)) AS cohort_month,
                amount
            FROM read_csv_auto('data/sample_sales.csv')
        )
        SELECT 
            cohort_month,
            COUNT(DISTINCT customer_id) AS active_customers,
            ROUND(SUM(amount), 2) AS monthly_revenue,
            ROUND(AVG(amount), 2) AS avg_customer_spend
        FROM monthly_cohorts
        GROUP BY cohort_month
        ORDER BY cohort_month ASC;
    """.trimIndent()

    private fun generateOlapAggregationSql(): String = """
        -- Category Revenue Breakdown with Window Percentiles
        SELECT 
            category,
            region,
            ROUND(SUM(amount), 2) AS category_revenue,
            ROUND(SUM(amount) * 100.0 / SUM(SUM(amount)) OVER(), 2) AS pct_of_total_revenue
        FROM read_csv_auto('data/sample_sales.csv')
        GROUP BY category, region
        ORDER BY category_revenue DESC;
    """.trimIndent()

    private fun generateOlapPipelinePy(): String = """
        import polars as pl

        def run_etl():
            print("Reading data/sample_sales.csv with Polars LazyFrame...")
            q = (
                pl.scan_csv("data/sample_sales.csv")
                .filter(pl.col("amount") > 50.0)
                .group_by(["region", "category"])
                .agg([
                    pl.len().alias("order_count"),
                    pl.col("amount").sum().round(2).alias("total_revenue"),
                    pl.col("amount").mean().round(2).alias("avg_spend")
                ])
                .sort("total_revenue", descending=True)
            )
            df = q.collect()
            print("ETL transformation complete:")
            print(df)
            return df

        if __name__ == "__main__":
            run_etl()
    """.trimIndent()

    private fun generateGenAiNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: Google Gemini Multimodal Studio 🤖\n\nInteract with Gemini models, generate structured outputs, and explore RAG embeddings."
        )
        model.addCell(
            CellType.CODE,
            """
                import os
                from google import genai

                # Initialize Gemini client
                # Ensure GEMINI_API_KEY is configured in your environment or .env file
                api_key = os.getenv("GEMINI_API_KEY", "")
                if not api_key:
                    print("⚠️ GEMINI_API_KEY not detected. Set it in .env or system environment.")
                else:
                    client = genai.Client(api_key=api_key)
                    response = client.models.generate_content(
                        model="gemini-2.5-flash",
                        contents="Explain the concept of zero-copy Arrow memory buffers in one concise sentence."
                    )
                    print("Gemini Response:\n", response.text)
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateGeminiAgentPy(): String = """
        import os
        from google import genai

        class GeminiDataAssistant:
            '''Conversational Assistant powered by Google Gemini.'''
            def __init__(self, api_key: str = None, model: str = "gemini-2.5-flash"):
                self.api_key = api_key or os.getenv("GEMINI_API_KEY", "")
                self.model = model
                self.client = genai.Client(api_key=self.api_key) if self.api_key else None

            def ask(self, prompt: str) -> str:
                if not self.client:
                    return "Error: Gemini client not initialized. Please provide GEMINI_API_KEY."
                response = self.client.models.generate_content(
                    model=self.model,
                    contents=prompt
                )
                return response.text
    """.trimIndent()

    private fun generateVectorStorePy(): String = """
        import numpy as np

        class SimpleVectorStore:
            '''In-memory cosine similarity vector index.'''
            def __init__(self):
                self.documents = []
                self.embeddings = []

            def add(self, doc: str, embedding: list[float]):
                self.documents.append(doc)
                self.embeddings.append(embedding)

            def search(self, query_emb: list[float], top_k: int = 3) -> list[tuple[str, float]]:
                if not self.embeddings:
                    return []
                q = np.array(query_emb)
                embs = np.array(self.embeddings)
                norms = np.linalg.norm(embs, axis=1) * np.linalg.norm(q)
                scores = np.dot(embs, q) / np.maximum(norms, 1e-9)
                top_indices = np.argsort(scores)[::-1][:top_k]
                return [(self.documents[i], float(scores[i])) for i in top_indices]
    """.trimIndent()

    private fun generateSystemInstructionsMd(): String = """
        # System Instructions for Data Science Copilot

        You are an expert Data Science and Machine Learning assistant integrated into Jörmungandr IDE.
        - Prioritize vectorized code (NumPy, Polars, DuckDB) over slow standard Python loops.
        - Provide concise explanations with copyable code snippets.
        - Highlight potential data leakage, multicollinearity, and overfitting risks.
    """.trimIndent()

    private fun generateJupyterQuickstartNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# Welcome to $projectName 🪐\n\nExplore interactive computing, variable inspection, and rich plots inside Jörmungandr."
        )
        model.addCell(
            CellType.CODE,
            """
                import numpy as np
                import pandas as pd
                import matplotlib.pyplot as plt

                # 1. Generate signal data
                t = np.linspace(0, 10, 500)
                signal = np.sin(t) + 0.3 * np.random.normal(size=len(t))

                plt.figure(figsize=(10, 4))
                plt.plot(t, signal, color="#268bd2", label="Noisy Signal")
                plt.plot(t, np.sin(t), color="#dc322f", linewidth=2, label="True Sine Wave")
                plt.title("Interactive Signal Plot")
                plt.xlabel("Time (s)")
                plt.ylabel("Amplitude")
                plt.legend()
                plt.show()
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateJupyterVizNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: Interactive Data Visualization Studio 📊\n\nGenerate distributions, boxplots, and scatter charts sent directly to Scientific Plots."
        )
        model.addCell(
            CellType.CODE,
            """
                import pandas as pd
                import seaborn as sns
                import matplotlib.pyplot as plt

                df_iris = pd.read_csv('data/iris.csv')
                print("Loaded Iris Dataset:")
                df_iris.head()
            """.trimIndent()
        )
        model.addCell(
            CellType.CODE,
            """
                plt.figure(figsize=(8, 5))
                sns.scatterplot(data=df_iris, x='sepal_length', y='sepal_width', hue='species', style='species', s=70)
                plt.title("Sepal Dimensions by Species")
                plt.show()
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateSampleIrisCsv(): String = """
        sepal_length,sepal_width,petal_length,petal_width,species
        5.1,3.5,1.4,0.2,setosa
        4.9,3.0,1.4,0.2,setosa
        4.7,3.2,1.3,0.2,setosa
        7.0,3.2,4.7,1.4,versicolor
        6.4,3.2,4.5,1.5,versicolor
        6.9,3.1,4.9,1.5,versicolor
        6.3,3.3,6.0,2.5,virginica
        5.8,2.7,5.1,1.9,virginica
        7.1,3.0,5.9,2.1,virginica
    """.trimIndent()

    private fun generateJupyterUtilsPy(): String = """
        import matplotlib.pyplot as plt

        def configure_plot_style():
            '''Configures consistent styling for figures.'''
            plt.rcParams['font.size'] = 11
            plt.rcParams['figure.dpi'] = 100
    """.trimIndent()

    private fun generateVisionNotebook(projectName: String): String {
        val model = NotebookModel()
        model.addCell(
            CellType.MARKDOWN,
            "# $projectName: Computer Vision & Augmentation Studio 👁️\n\nExplore PyTorch Vision transforms and dataset augmentations."
        )
        model.addCell(
            CellType.CODE,
            """
                import torch
                import torchvision.transforms as T
                from PIL import Image
                import numpy as np
                import matplotlib.pyplot as plt

                # Define standard vision augmentation pipeline
                transform = T.Compose([
                    T.Resize((224, 224)),
                    T.RandomHorizontalFlip(p=0.5),
                    T.ColorJitter(brightness=0.2, contrast=0.2),
                    T.ToTensor()
                ])
                print("PyTorch vision pipeline ready for image tensors!")
            """.trimIndent()
        )
        return NotebookFormat.writeNotebook(model)
    }

    private fun generateVisionTransformsPy(): String = """
        import torchvision.transforms as T

        def get_train_transforms(image_size: int = 224):
            return T.Compose([
                T.Resize((image_size, image_size)),
                T.RandomHorizontalFlip(),
                T.RandomRotation(15),
                T.ToTensor(),
                T.Normalize(mean=[0.485, 0.456, 0.406], std=[0.229, 0.224, 0.225])
            ])
    """.trimIndent()

    private fun generateVisionBackbonePy(): String = """
        import torch
        import torch.nn as nn

        class SimpleConvNet(nn.Module):
            '''Lightweight Convolutional Feature Extractor.'''
            def __init__(self, num_classes: int = 10):
                super().__init__()
                self.features = nn.Sequential(
                    nn.Conv2d(3, 32, kernel_size=3, padding=1),
                    nn.BatchNorm2d(32),
                    nn.ReLU(),
                    nn.MaxPool2d(2, 2),
                    nn.Conv2d(32, 64, kernel_size=3, padding=1),
                    nn.BatchNorm2d(64),
                    nn.ReLU(),
                    nn.AdaptiveAvgPool2d((1, 1))
                )
                self.classifier = nn.Linear(64, num_classes)

            def forward(self, x):
                feat = self.features(x)
                flat = torch.flatten(feat, 1)
                return self.classifier(flat)
    """.trimIndent()
}
