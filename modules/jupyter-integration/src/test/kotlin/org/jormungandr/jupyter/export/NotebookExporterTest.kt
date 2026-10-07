package org.jormungandr.jupyter.export

import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookExporterTest {

    private fun createSampleNotebook(): NotebookModel {
        val model = NotebookModel()
        model.addCell(CellType.MARKDOWN, "# Data Science Overview\nAnalysis of Iris dataset.")
        val codeCell = model.addCell(CellType.CODE, "import pandas as pd\ndf = pd.read_csv('data.csv')\nprint(df.shape)")
        codeCell.executionCount = 1
        codeCell.outputs.add(CellOutput.StreamOutput("stdout", "(150, 5)\n"))
        return model
    }

    @Test
    fun `test export to HTML generates valid HTML5 document`() {
        val model = createSampleNotebook()
        val html = NotebookExporter.exportToHtml(model, "Iris Analysis")

        assertTrue(html.contains("<!DOCTYPE html>"), "Must declare HTML5 DOCTYPE")
        assertTrue(html.contains("Iris Analysis - Jörmungandr Notebook"), "Must contain page title")
        assertTrue(html.contains("<h1>Data Science Overview</h1>"), "Must render H1 heading")
        assertTrue(html.contains("In [1]:"), "Must render input execution count prompt")
        assertTrue(html.contains("(150, 5)"), "Must render stdout text")
        assertTrue(html.contains("Jörmungandr IDE"), "Must contain brand attribution footer")
    }

    @Test
    fun `test export to Python script generates valid scientific markers`() {
        val model = createSampleNotebook()
        val py = NotebookExporter.exportToPython(model)

        assertTrue(py.startsWith("#!/usr/bin/env python"))
        assertTrue(py.contains("# %% [markdown]"), "Must generate markdown cell marker")
        assertTrue(py.contains("# # Data Science Overview"))
        assertTrue(py.contains("# %%"), "Must generate code cell marker")
        assertTrue(py.contains("import pandas as pd"))
    }

    @Test
    fun `test export to Markdown produces clean code blocks`() {
        val model = createSampleNotebook()
        val md = NotebookExporter.exportToMarkdown(model)

        assertTrue(md.contains("# Data Science Overview"))
        assertTrue(md.contains("```python\nimport pandas as pd"))
        assertTrue(md.contains("```text\n(150, 5)\n```"))
    }

    @Test
    fun `test export to LaTeX generates valid article structure`() {
        val model = createSampleNotebook()
        val tex = NotebookExporter.exportToLatex(model, "Iris Analysis")

        assertTrue(tex.contains("\\documentclass{article}"))
        assertTrue(tex.contains("\\section{Data Science Overview}"))
        assertTrue(tex.contains("\\begin{lstlisting}[language=Python]"))
        assertTrue(tex.contains("\\end{document}"))
    }
}
