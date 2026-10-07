package org.jormungandr.jupyter.export

import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel

/**
 * High-fidelity notebook multi-format export engine.
 * Converts [NotebookModel] into standalone HTML, executable Python scripts,
 * GitHub-flavored Markdown, and LaTeX documents.
 */
object NotebookExporter {

    /**
     * Exports the notebook as a clean, self-contained HTML document.
     */
    fun exportToHtml(model: NotebookModel, title: String = "Jupyter Notebook"): String {
        val bodyBuilder = StringBuilder()

        for (cell in model.cells) {
            when (cell.cellType) {
                CellType.MARKDOWN -> {
                    bodyBuilder.append("""
                        <div class="cell markdown-cell">
                            ${renderSimpleMarkdownToHtml(cell.source)}
                        </div>
                    """.trimIndent()).append("\n")
                }

                CellType.CODE -> {
                    val promptText = if (cell.executionCount != null) "In [${cell.executionCount}]:" else "In [ ]:"
                    val escapedCode = escapeHtml(cell.source)

                    bodyBuilder.append("""
                        <div class="cell code-cell">
                            <div class="input-area">
                                <div class="prompt input-prompt">$promptText</div>
                                <div class="code-container">
                                    <pre><code>$escapedCode</code></pre>
                                </div>
                            </div>
                    """.trimIndent()).append("\n")

                    if (cell.outputs.isNotEmpty()) {
                        bodyBuilder.append("""    <div class="output-area">""").append("\n")
                        for (out in cell.outputs) {
                            when (out) {
                                is CellOutput.StreamOutput -> {
                                    val safeText = escapeHtml(stripAnsi(out.text))
                                    bodyBuilder.append("""
                                        <div class="output-stream ${if (out.name == "stderr") "stderr" else "stdout"}">
                                            <pre>$safeText</pre>
                                        </div>
                                    """.trimIndent()).append("\n")
                                }

                                is CellOutput.ExecuteResultOutput -> {
                                    val outPrompt = "Out [${out.executionCount}]:"
                                    val png = out.getPngBase64()
                                    val html = out.getHtml()
                                    val text = out.getPlainText()

                                    bodyBuilder.append("""        <div class="output-result">""").append("\n")
                                    bodyBuilder.append("""            <div class="prompt output-prompt">$outPrompt</div>""").append("\n")
                                    bodyBuilder.append("""            <div class="output-content">""").append("\n")
                                    when {
                                        png != null -> {
                                            bodyBuilder.append("""                <img class="output-image" src="data:image/png;base64,$png" alt="Plot output" />""").append("\n")
                                        }
                                        html != null -> {
                                            bodyBuilder.append("""                <div class="output-html">$html</div>""").append("\n")
                                        }
                                        text != null -> {
                                            bodyBuilder.append("""                <pre>${escapeHtml(stripAnsi(text))}</pre>""").append("\n")
                                        }
                                    }
                                    bodyBuilder.append("""            </div>""").append("\n")
                                    bodyBuilder.append("""        </div>""").append("\n")
                                }

                                is CellOutput.DisplayDataOutput -> {
                                    val png = out.getPngBase64()
                                    val html = out.getHtml()
                                    val text = out.getPlainText()

                                    bodyBuilder.append("""        <div class="output-display">""").append("\n")
                                    when {
                                        png != null -> {
                                            bodyBuilder.append("""            <img class="output-image" src="data:image/png;base64,$png" alt="Display output" />""").append("\n")
                                        }
                                        html != null -> {
                                            bodyBuilder.append("""            <div class="output-html">$html</div>""").append("\n")
                                        }
                                        text != null -> {
                                            bodyBuilder.append("""            <pre>${escapeHtml(stripAnsi(text))}</pre>""").append("\n")
                                        }
                                    }
                                    bodyBuilder.append("""        </div>""").append("\n")
                                }

                                is CellOutput.ErrorOutput -> {
                                    val errorText = escapeHtml(stripAnsi("${out.ename}: ${out.evalue}\n" + out.traceback.joinToString("\n")))
                                    bodyBuilder.append("""
                                        <div class="output-error">
                                            <pre>$errorText</pre>
                                        </div>
                                    """.trimIndent()).append("\n")
                                }
                            }
                        }
                        bodyBuilder.append("""    </div>""").append("\n")
                    }

                    bodyBuilder.append("""</div>""").append("\n")
                }

                CellType.RAW -> {
                    bodyBuilder.append("""
                        <div class="cell raw-cell">
                            <pre>${escapeHtml(cell.source)}</pre>
                        </div>
                    """.trimIndent()).append("\n")
                }
            }
        }

        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>${escapeHtml(title)} - Jörmungandr Notebook</title>
                <style>
                    :root {
                        --bg-color: #fdf6e3;
                        --card-bg: #ffffff;
                        --text-color: #073642;
                        --accent-color: #268bd2;
                        --code-bg: #f8f9fa;
                        --border-color: #e2e8f0;
                        --prompt-color: #93a1a1;
                    }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        background-color: var(--bg-color);
                        color: var(--text-color);
                        margin: 0;
                        padding: 32px 16px;
                    }
                    .container {
                        max-width: 960px;
                        margin: 0 auto;
                        background: var(--card-bg);
                        border-radius: 8px;
                        box-shadow: 0 4px 16px rgba(0,0,0,0.06);
                        padding: 32px 40px;
                        border: 1px solid var(--border-color);
                    }
                    .header-banner {
                        border-bottom: 2px solid var(--border-color);
                        padding-bottom: 16px;
                        margin-bottom: 24px;
                    }
                    .header-banner h1 {
                        margin: 0 0 6px 0;
                        color: #2aa198;
                        font-size: 24px;
                    }
                    .header-meta {
                        color: var(--prompt-color);
                        font-size: 12px;
                    }
                    .cell {
                        margin-bottom: 20px;
                    }
                    .markdown-cell {
                        line-height: 1.6;
                        color: #2c3e50;
                    }
                    .markdown-cell h1, .markdown-cell h2, .markdown-cell h3 {
                        color: #073642;
                        margin-top: 16px;
                        margin-bottom: 8px;
                    }
                    .markdown-cell code {
                        background: #eee8d5;
                        padding: 2px 5px;
                        border-radius: 4px;
                        font-family: "JetBrains Mono", Consolas, monospace;
                        font-size: 13px;
                    }
                    .input-area {
                        display: flex;
                        background: var(--code-bg);
                        border: 1px solid var(--border-color);
                        border-radius: 6px;
                        overflow: hidden;
                    }
                    .prompt {
                        width: 90px;
                        padding: 10px 8px;
                        font-family: "JetBrains Mono", Consolas, monospace;
                        font-size: 11px;
                        font-weight: bold;
                        user-select: none;
                        text-align: right;
                        box-sizing: border-box;
                    }
                    .input-prompt {
                        color: var(--accent-color);
                        background: #f1f5f9;
                        border-right: 1px solid var(--border-color);
                    }
                    .output-prompt {
                        color: #dc322f;
                    }
                    .code-container {
                        flex: 1;
                        padding: 10px 14px;
                        overflow-x: auto;
                    }
                    pre {
                        margin: 0;
                        font-family: "JetBrains Mono", Consolas, monospace;
                        font-size: 13px;
                        line-height: 1.45;
                    }
                    .output-area {
                        margin-top: 6px;
                        margin-left: 90px;
                    }
                    .output-stream {
                        padding: 6px 10px;
                        font-family: "JetBrains Mono", Consolas, monospace;
                        font-size: 12px;
                        background: #f8f9fa;
                        border-radius: 4px;
                    }
                    .output-stream.stderr {
                        background: #fee2e2;
                        color: #991b1b;
                    }
                    .output-result {
                        display: flex;
                    }
                    .output-content {
                        flex: 1;
                        padding: 4px 0;
                        overflow-x: auto;
                    }
                    .output-image {
                        max-width: 100%;
                        height: auto;
                        border-radius: 4px;
                        border: 1px solid #e2e8f0;
                        margin-top: 6px;
                    }
                    .output-error {
                        background: #fef2f2;
                        border-left: 4px solid #ef4444;
                        color: #b91c1c;
                        padding: 8px 12px;
                        border-radius: 0 4px 4px 0;
                    }
                    .footer {
                        margin-top: 40px;
                        padding-top: 16px;
                        border-top: 1px solid var(--border-color);
                        font-size: 11px;
                        color: var(--prompt-color);
                        text-align: center;
                    }
                </style>
            </head>
            <body>
                <div class="container">
                    <div class="header-banner">
                        <h1>🪐 ${escapeHtml(title)}</h1>
                        <div class="header-meta">Generated with Jörmungandr Data Science Studio</div>
                    </div>
                    $bodyBuilder
                    <div class="footer">
                        Rendered by 🐍 <b>Jörmungandr IDE</b> • The Modular Open-Source Python &amp; Data Science Platform
                    </div>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    /**
     * Exports the notebook as an executable Python script with scientific `# %%` cell markers.
     */
    fun exportToPython(model: NotebookModel): String {
        val sb = StringBuilder()
        sb.append("#!/usr/bin/env python\n")
        sb.append("# coding: utf-8\n")
        sb.append("# Generated with Jörmungandr Jupyter Studio 🐍\n\n")

        for (cell in model.cells) {
            when (cell.cellType) {
                CellType.CODE -> {
                    sb.append("# %%\n")
                    sb.append(cell.source.trimEnd())
                    sb.append("\n\n")
                }

                CellType.MARKDOWN -> {
                    sb.append("# %% [markdown]\n")
                    for (line in cell.source.lines()) {
                        sb.append("# $line\n")
                    }
                    sb.append("\n")
                }

                CellType.RAW -> {
                    sb.append("# %% [raw]\n")
                    for (line in cell.source.lines()) {
                        sb.append("# $line\n")
                    }
                    sb.append("\n")
                }
            }
        }

        return sb.toString()
    }

    /**
     * Exports the notebook as a GitHub-flavored Markdown document.
     */
    fun exportToMarkdown(model: NotebookModel): String {
        val sb = StringBuilder()
        sb.append("<!-- Generated with Jörmungandr Jupyter Studio 🐍 -->\n\n")

        for (cell in model.cells) {
            when (cell.cellType) {
                CellType.MARKDOWN -> {
                    sb.append(cell.source.trim())
                    sb.append("\n\n")
                }

                CellType.CODE -> {
                    sb.append("```python\n")
                    sb.append(cell.source.trim())
                    sb.append("\n```\n\n")

                    // Append text outputs if present
                    val plainOutputs = cell.outputs.mapNotNull {
                        when (it) {
                            is CellOutput.StreamOutput -> stripAnsi(it.text)
                            is CellOutput.ExecuteResultOutput -> it.getPlainText()?.let { t -> stripAnsi(t) }
                            is CellOutput.DisplayDataOutput -> it.getPlainText()?.let { t -> stripAnsi(t) }
                            is CellOutput.ErrorOutput -> "${it.ename}: ${it.evalue}"
                        }
                    }

                    if (plainOutputs.isNotEmpty()) {
                        sb.append("```text\n")
                        sb.append(plainOutputs.joinToString("\n").trim())
                        sb.append("\n```\n\n")
                    }
                }

                CellType.RAW -> {
                    sb.append("```\n")
                    sb.append(cell.source.trim())
                    sb.append("\n```\n\n")
                }
            }
        }

        return sb.toString()
    }

    /**
     * Exports the notebook as a LaTeX document.
     */
    fun exportToLatex(model: NotebookModel, title: String = "Jupyter Notebook"): String {
        val sb = StringBuilder()
        val safeTitle = escapeLatex(title)

        sb.append("""
            \documentclass{article}
            \usepackage[utf8]{inputenc}
            \usepackage{listings}
            \usepackage{xcolor}
            \usepackage{graphicx}
            \usepackage{geometry}
            \geometry{margin=1in}

            \definecolor{codegreen}{rgb}{0,0.6,0}
            \definecolor{codegray}{rgb}{0.5,0.5,0.5}
            \definecolor{codepurple}{rgb}{0.58,0,0.82}
            \definecolor{backcolour}{rgb}{0.97,0.97,0.97}

            \lstdefinestyle{mystyle}{
                backgroundcolor=\color{backcolour},
                commentstyle=\color{codegreen},
                keywordstyle=\color{blue},
                numberstyle=\tiny\color{codegray},
                stringstyle=\color{codepurple},
                basicstyle=\ttfamily\footnotesize,
                breakatwhitespace=false,
                breaklines=true,
                keepspaces=true,
                numbers=left,
                numbersep=5pt,
                showspaces=false,
                showstringspaces=false,
                showtabs=false,
                tabsize=2
            }
            \lstset{style=mystyle}

            \title{$safeTitle}
            \author{Generated by J\"ormungandr IDE}
            \date{\today}

            \begin{document}
            \maketitle

        """.trimIndent()).append("\n\n")

        for (cell in model.cells) {
            when (cell.cellType) {
                CellType.MARKDOWN -> {
                    for (line in cell.source.lines()) {
                        val trimmed = line.trim()
                        when {
                            trimmed.startsWith("# ") -> sb.append("\\section{${escapeLatex(trimmed.removePrefix("# ").trim())}}\n")
                            trimmed.startsWith("## ") -> sb.append("\\subsection{${escapeLatex(trimmed.removePrefix("## ").trim())}}\n")
                            trimmed.startsWith("### ") -> sb.append("\\subsubsection{${escapeLatex(trimmed.removePrefix("### ").trim())}}\n")
                            trimmed.isNotEmpty() -> sb.append("${escapeLatex(trimmed)}\n\n")
                        }
                    }
                }

                CellType.CODE -> {
                    sb.append("\\begin{lstlisting}[language=Python]\n")
                    sb.append(cell.source)
                    sb.append("\n\\end{lstlisting}\n\n")
                }

                CellType.RAW -> {
                    sb.append("\\begin{verbatim}\n")
                    sb.append(cell.source)
                    sb.append("\n\\end{verbatim}\n\n")
                }
            }
        }

        sb.append("\\end{document}\n")
        return sb.toString()
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;")
    }

    private fun escapeLatex(text: String): String {
        return text.replace("\\", "\\textbackslash{}")
            .replace("_", "\\_")
            .replace("%", "\\%")
            .replace("$", "\\$")
            .replace("#", "\\#")
            .replace("&", "\\&")
            .replace("{", "\\{")
            .replace("}", "\\}")
    }

    private fun stripAnsi(text: String): String {
        return text.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "")
    }

    private fun renderSimpleMarkdownToHtml(markdown: String): String {
        val lines = markdown.lines()
        val sb = StringBuilder()

        for (line in lines) {
            val trimmed = line.trim()
            when {
                trimmed.startsWith("# ") -> sb.append("<h1>${escapeHtml(trimmed.removePrefix("# "))}</h1>\n")
                trimmed.startsWith("## ") -> sb.append("<h2>${escapeHtml(trimmed.removePrefix("## "))}</h2>\n")
                trimmed.startsWith("### ") -> sb.append("<h3>${escapeHtml(trimmed.removePrefix("### "))}</h3>\n")
                trimmed.startsWith("#### ") -> sb.append("<h4>${escapeHtml(trimmed.removePrefix("#### "))}</h4>\n")
                trimmed.startsWith("- ") -> sb.append("<li>${escapeHtml(trimmed.removePrefix("- "))}</li>\n")
                trimmed.startsWith("* ") -> sb.append("<li>${escapeHtml(trimmed.removePrefix("* "))}</li>\n")
                trimmed.isNotEmpty() -> sb.append("<p>${escapeHtml(trimmed)}</p>\n")
            }
        }
        return sb.toString()
    }
}
