package org.jormungandr.shell.project

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataScienceProjectScaffolderTest {

    @Test
    fun `test all templates generate valid files`() {
        for (template in DataScienceProjectTemplate.entries) {
            val files = template.generateFiles("TestProject")
            assertTrue(files.isNotEmpty(), "Template ${template.id} should generate files")
            assertTrue(files.any { it.relativePath == "README.md" }, "Template ${template.id} must include README.md")
            assertTrue(files.any { it.relativePath == "pyproject.toml" }, "Template ${template.id} must include pyproject.toml")
            assertTrue(files.any { it.relativePath == "requirements.txt" }, "Template ${template.id} must include requirements.txt")
            assertTrue(files.any { it.relativePath == ".gitignore" }, "Template ${template.id} must include .gitignore")
            assertTrue(files.any { it.isPrimaryToOpen }, "Template ${template.id} must designate a primary file to open")
        }
    }

    @Test
    fun `test scaffolding machine learning project`(@TempDir tempDir: File) {
        val targetDir = File(tempDir, "ml_demo")
        val options = ScaffoldOptions(
            projectName = "ml_demo",
            targetDirectory = targetDir,
            template = DataScienceProjectTemplate.MACHINE_LEARNING,
            environmentType = EnvironmentType.UV,
            initGit = false,
            autoCreateVenv = false
        )

        val result = DataScienceProjectScaffolder.scaffold(options)
        assertTrue(result.success, "Scaffolding should succeed")
        assertTrue(targetDir.exists())
        assertTrue(File(targetDir, "notebooks/01_exploratory_analysis.ipynb").exists())
        assertTrue(File(targetDir, "notebooks/02_model_training.ipynb").exists())
        assertTrue(File(targetDir, "src/train.py").exists())
        assertTrue(File(targetDir, "src/dataset.py").exists())
        assertTrue(File(targetDir, "tools/setup_env.ps1").exists())
        assertEquals(File(targetDir, "notebooks/01_exploratory_analysis.ipynb"), result.primaryFile)
    }

    @Test
    fun `test scaffolding tabular olap project`(@TempDir tempDir: File) {
        val targetDir = File(tempDir, "olap_demo")
        val options = ScaffoldOptions(
            projectName = "olap_demo",
            targetDirectory = targetDir,
            template = DataScienceProjectTemplate.TABULAR_OLAP,
            environmentType = EnvironmentType.VENV,
            initGit = false,
            autoCreateVenv = false
        )

        val result = DataScienceProjectScaffolder.scaffold(options)
        assertTrue(result.success)
        assertTrue(File(targetDir, "notebooks/01_duckdb_analytics.ipynb").exists())
        assertTrue(File(targetDir, "data/sample_sales.csv").exists())
        assertTrue(File(targetDir, "queries/retention_analysis.sql").exists())
        assertTrue(File(targetDir, "tools/setup_env.ps1").exists())
    }

    @Test
    fun `test scaffolding genai llm project`(@TempDir tempDir: File) {
        val targetDir = File(tempDir, "genai_demo")
        val options = ScaffoldOptions(
            projectName = "genai_demo",
            targetDirectory = targetDir,
            template = DataScienceProjectTemplate.GENAI_LLM,
            environmentType = EnvironmentType.CONDA,
            initGit = false,
            autoCreateVenv = false
        )

        val result = DataScienceProjectScaffolder.scaffold(options)
        assertTrue(result.success)
        assertTrue(File(targetDir, "notebooks/01_gemini_multimodal.ipynb").exists())
        assertTrue(File(targetDir, "src/gemini_agent.py").exists())
        assertTrue(File(targetDir, "environment.yml").exists())
    }

    @Test
    fun `test action presentation and metadata`() {
        val action = NewDataScienceProjectAction()
        assertNotNull(action.templateText)
        assertTrue(action.templateText!!.contains("Data Science Workspace"))
    }
}
