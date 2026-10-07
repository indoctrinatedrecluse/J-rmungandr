/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.jupyter.ui.render

import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import java.awt.*
import java.awt.datatransfer.StringSelection
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder

/**
 * High-fidelity mathematical formula renderer supporting:
 * - LaTeX block math (`$$ ... $$`, `\[ ... \]`, `text/latex`)
 * - Inline math (`$ ... $`, `\( ... \)`)
 * - Rich typography rendering with Unicode math symbols and fractional layout
 * - Standalone MathJax / KaTeX HTML generation for export and web preview
 */
object NotebookMathRenderer {

    private val GREEK_AND_OPERATOR_SYMBOLS = mapOf(
        "\\alpha" to "α",
        "\\beta" to "β",
        "\\gamma" to "γ",
        "\\delta" to "δ",
        "\\epsilon" to "ε",
        "\\zeta" to "ζ",
        "\\eta" to "η",
        "\\theta" to "θ",
        "\\iota" to "ι",
        "\\kappa" to "κ",
        "\\lambda" to "λ",
        "\\mu" to "μ",
        "\\nu" to "ν",
        "\\xi" to "ξ",
        "\\pi" to "π",
        "\\rho" to "ρ",
        "\\sigma" to "σ",
        "\\tau" to "τ",
        "\\upsilon" to "υ",
        "\\phi" to "ϕ",
        "\\chi" to "χ",
        "\\psi" to "ψ",
        "\\omega" to "ω",
        "\\Gamma" to "Γ",
        "\\Delta" to "Δ",
        "\\Theta" to "Θ",
        "\\Lambda" to "Λ",
        "\\Xi" to "Ξ",
        "\\Pi" to "Π",
        "\\Sigma" to "Σ",
        "\\Upsilon" to "Υ",
        "\\Phi" to "Φ",
        "\\Psi" to "Ψ",
        "\\Omega" to "Ω",
        "\\sum" to "∑",
        "\\int" to "∫",
        "\\iint" to "∬",
        "\\iiint" to "∭",
        "\\oint" to "∮",
        "\\prod" to "∏",
        "\\coprod" to "∐",
        "\\lim" to "lim",
        "\\infty" to "∞",
        "\\partial" to "∂",
        "\\nabla" to "∇",
        "\\pm" to "±",
        "\\times" to "×",
        "\\div" to "÷",
        "\\cdot" to "·",
        "\\approx" to "≈",
        "\\equiv" to "≡",
        "\\neq" to "≠",
        "\\ne" to "≠",
        "\\leq" to "≤",
        "\\le" to "≤",
        "\\geq" to "≥",
        "\\ge" to "≥",
        "\\ll" to "≪",
        "\\gg" to "≫",
        "\\subset" to "⊂",
        "\\supset" to "⊃",
        "\\subseteq" to "⊆",
        "\\supseteq" to "⊇",
        "\\in" to "∈",
        "\\notin" to "∉",
        "\\cup" to "∪",
        "\\cap" to "∩",
        "\\forall" to "∀",
        "\\exists" to "∃",
        "\\neg" to "¬",
        "\\to" to "→",
        "\\rightarrow" to "→",
        "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒",
        "\\Leftarrow" to "⇐",
        "\\iff" to "⇔",
        "\\leftrightarrow" to "↔",
        "\\sin" to "sin",
        "\\cos" to "cos",
        "\\tan" to "tan",
        "\\log" to "log",
        "\\ln" to "ln",
        "\\exp" to "exp",
        "\\det" to "det",
        "\\quad" to "&nbsp;&nbsp;&nbsp;&nbsp;",
        "\\qquad" to "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;",
        "\\," to "&nbsp;",
        "\\;" to "&nbsp;&nbsp;",
        "\\{" to "{",
        "\\}" to "}"
    )

    /**
     * Translates a LaTeX expression into an HTML fragment styled for Swing [JEditorPane].
     */
    fun renderLatexToHtml(latexRaw: String): String {
        var s = latexRaw.trim()
        if (s.startsWith("$$") && s.endsWith("$$") && s.length >= 4) {
            s = s.substring(2, s.length - 2).trim()
        } else if (s.startsWith("\\[") && s.endsWith("\\]") && s.length >= 4) {
            s = s.substring(2, s.length - 2).trim()
        } else if (s.startsWith("$") && s.endsWith("$") && s.length >= 2) {
            s = s.substring(1, s.length - 1).trim()
        } else if (s.startsWith("\\(") && s.endsWith("\\)") && s.length >= 4) {
            s = s.substring(2, s.length - 2).trim()
        }

        // Replace Fractions \frac{num}{den} with balanced brace support
        while (s.contains("\\frac")) {
            val idx = s.indexOf("\\frac")
            val brace1Start = s.indexOf('{', idx + 5)
            if (brace1Start != -1 && s.substring(idx + 5, brace1Start).isBlank()) {
                val arg1 = extractBalancedBlock(s, brace1Start)
                if (arg1 != null) {
                    val brace2Start = s.indexOf('{', arg1.second + 1)
                    if (brace2Start != -1 && s.substring(arg1.second + 1, brace2Start).isBlank()) {
                        val arg2 = extractBalancedBlock(s, brace2Start)
                        if (arg2 != null) {
                            val num = renderLatexToHtml(arg1.first)
                            val den = renderLatexToHtml(arg2.first)
                            val replacement = "<table style=\"display:inline-table;vertical-align:middle;text-align:center;border-collapse:collapse;margin:0 4px;\">" +
                                "<tr><td style=\"border-bottom:1px solid #334155;padding:0 4px;font-size:13px;\">$num</td></tr>" +
                                "<tr><td style=\"padding:0 4px;font-size:13px;\">$den</td></tr>" +
                            "</table>"
                            s = s.substring(0, idx) + replacement + s.substring(arg2.second + 1)
                            continue
                        }
                    }
                }
            }
            break
        }

        // Replace Square roots \sqrt{x} with balanced brace support
        while (s.contains("\\sqrt")) {
            val idx = s.indexOf("\\sqrt")
            val braceStart = s.indexOf('{', idx + 5)
            if (braceStart != -1 && s.substring(idx + 5, braceStart).isBlank()) {
                val arg = extractBalancedBlock(s, braceStart)
                if (arg != null) {
                    val expr = renderLatexToHtml(arg.first)
                    val replacement = "√<span style=\"border-top:1px solid #334155;padding-top:1px;\">$expr</span>"
                    s = s.substring(0, idx) + replacement + s.substring(arg.second + 1)
                    continue
                }
            }
            break
        }

        // Replace Superscripts x^{abc} or x^2
        s = s.replace(Regex("""\^\{([^{}]+)\}""")) { "<sup>${it.groupValues[1]}</sup>" }
        s = s.replace(Regex("""\^([a-zA-Z0-9])""")) { "<sup>${it.groupValues[1]}</sup>" }

        // Replace Subscripts x_{abc} or x_i
        s = s.replace(Regex("""_\{([^{}]+)\}""")) { "<sub>${it.groupValues[1]}</sub>" }
        s = s.replace(Regex("""_([a-zA-Z0-9])""")) { "<sub>${it.groupValues[1]}</sub>" }

        // Replace matrices
        s = s.replace(Regex("""\\begin\{[pbvV]?matrix\}(.*?)\\end\{[pbvV]?matrix\}""", RegexOption.DOT_MATCHES_ALL)) { match ->
            val content = match.groupValues[1].trim()
            val rows = content.split("\\\\").filter { it.isNotBlank() }
            val tableRows = rows.joinToString("") { row ->
                val cells = row.split("&").map { it.trim() }
                "<tr>" + cells.joinToString("") { "<td style=\"padding:2px 8px;text-align:center;\">$it</td>" } + "</tr>"
            }
            "<table style=\"display:inline-table;border-left:2px solid #334155;border-right:2px solid #334155;margin:0 4px;vertical-align:middle;\">$tableRows</table>"
        }

        // Replace LaTeX macros and symbols
        for ((macro, symbol) in GREEK_AND_OPERATOR_SYMBOLS) {
            s = s.replace(macro, symbol)
        }

        // Clean leftover braces
        s = s.replace("\\left(", "(").replace("\\right)", ")")
        s = s.replace("\\left[", "[").replace("\\right]", "]")
        s = s.replace("\\left\\{", "{").replace("\\right\\}", "}")
        s = s.replace("\\left.", "").replace("\\right.", "")

        return s
    }

    /**
     * Formats full HTML with MathJax CDN support for webviews and HTML export.
     */
    fun generateStandaloneMathJaxHtml(latex: String, isBlock: Boolean = true): String {
        val mathDelim = if (isBlock) "$$$latex$$$" else "$$latex$"
        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<script>
window.MathJax = {
  tex: {
    inlineMath: [['$', '$'], ['\\(', '\\)']],
    displayMath: [['$$', '$$'], ['\\[', '\\]']]
  },
  svg: { fontCache: 'global' }
};
</script>
<script id="MathJax-script" async src="https://cdn.jsdelivr.net/npm/mathjax@3/es5/tex-mml-chtml.js"></script>
<style>
body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    padding: 12px;
    margin: 0;
    color: #1e293b;
    background: transparent;
}
.math-container {
    font-size: 1.15em;
    text-align: ${if (isBlock) "center" else "left"};
    padding: 8px;
}
</style>
</head>
<body>
<div class="math-container">$mathDelim</div>
</body>
</html>
        """.trimIndent()
    }

    /**
     * Creates an interactive Swing component card displaying the mathematical formula.
     */
    fun createLatexCard(latex: String, execCount: Int? = null): JPanel {
        val card = JPanel(BorderLayout()).apply {
            isOpaque = true
            background = Color(255, 255, 255)
            border = CompoundBorder(
                LineBorder(Color(203, 213, 225), 1, true),
                EmptyBorder(6, 8, 6, 8)
            )
        }

        // Header
        val header = JPanel(BorderLayout()).apply { isOpaque = false }
        val titleText = if (execCount != null) "📐 LaTeX Formula [Out $execCount]" else "📐 Mathematical Formula (LaTeX)"
        val titleLabel = JBLabel(titleText).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 41, 59)
        }
        header.add(titleLabel, BorderLayout.WEST)

        val actions = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val copyLatexBtn = JButton("📋 Copy LaTeX").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                val sel = StringSelection(latex)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }

        val rawSourceArea = JBTextArea(latex).apply {
            font = Font("Monospaced", Font.PLAIN, 11)
            isEditable = false
            background = Color(248, 250, 252)
            margin = Insets(4, 6, 4, 6)
        }
        val rawScroll = JBScrollPane(rawSourceArea).apply {
            border = LineBorder(Color(226, 232, 240), 1)
            isVisible = false
        }

        val toggleSourceBtn = JButton("👁️ Source").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                rawScroll.isVisible = !rawScroll.isVisible
                card.revalidate()
                card.repaint()
            }
        }

        actions.add(copyLatexBtn)
        actions.add(toggleSourceBtn)
        header.add(actions, BorderLayout.EAST)
        card.add(header, BorderLayout.NORTH)

        // Math Display
        val renderedHtml = renderLatexToHtml(latex)
        val htmlContent = "<html><body style=\"font-family:'Cambria Math', 'Times New Roman', serif; font-size:15px; color:#0f172a; padding:6px; text-align:center;\">$renderedHtml</body></html>"
        val mathPane = JEditorPane("text/html", htmlContent).apply {
            isEditable = false
            background = Color(255, 255, 255)
            border = EmptyBorder(6, 6, 6, 6)
        }

        val centerPanel = JPanel(BorderLayout(0, 4)).apply {
            isOpaque = false
            add(mathPane, BorderLayout.CENTER)
            add(rawScroll, BorderLayout.SOUTH)
        }

        card.add(centerPanel, BorderLayout.CENTER)
        return card
    }

    private fun extractBalancedBlock(text: String, startIndex: Int): Pair<String, Int>? {
        if (startIndex !in text.indices || text[startIndex] != '{') return null
        var depth = 0
        var i = startIndex
        val start = startIndex + 1
        while (i < text.length) {
            if (text[i] == '{') {
                depth++
            } else if (text[i] == '}') {
                depth--
                if (depth == 0) {
                    return Pair(text.substring(start, i), i)
                }
            }
            i++
        }
        return null
    }
}
