package org.jormungandr.core.theme

/**
 * Core surface and text color tokens.
 */
data class ColorTokens(
    val background: String,
    val secondaryBackground: String,
    val surface: String,
    val foreground: String,
    val mutedForeground: String,
    val border: String,
    val accent: String,
    val accentHover: String,
    val selectionBackground: String,
    val selectionForeground: String,
    val error: String,
    val warning: String,
    val success: String,
    val info: String
)

/**
 * Tabular and dataframe grid styling tokens.
 */
data class DataGridThemeTokens(
    val headerBackground: String,
    val headerForeground: String,
    val rowEvenBackground: String,
    val rowOddBackground: String,
    val gridLineColor: String,
    val selectionBackground: String,
    val selectionForeground: String
)

/**
 * Syntax highlighting tokens for code, queries, and notebooks.
 */
data class SyntaxThemeTokens(
    val keyword: String,
    val string: String,
    val number: String,
    val comment: String,
    val function: String,
    val variable: String,
    val type: String
)

/**
 * Universal theme specification applied across all IDE windows,
 * perspectives, and modular extensions.
 */
data class JormungandrTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val colors: ColorTokens,
    val dataGrid: DataGridThemeTokens,
    val syntax: SyntaxThemeTokens,
    val chartPalette: List<String>
) {
    /**
     * Serializes theme tokens into standard CSS custom properties
     * for embedding in JCEF webviews, Jupyter notebook cells, and charts.
     */
    fun toCssVariables(): String = buildString {
        appendLine(":root {")
        appendLine("  --jg-bg: ${colors.background};")
        appendLine("  --jg-bg-secondary: ${colors.secondaryBackground};")
        appendLine("  --jg-surface: ${colors.surface};")
        appendLine("  --jg-fg: ${colors.foreground};")
        appendLine("  --jg-fg-muted: ${colors.mutedForeground};")
        appendLine("  --jg-border: ${colors.border};")
        appendLine("  --jg-accent: ${colors.accent};")
        appendLine("  --jg-accent-hover: ${colors.accentHover};")
        appendLine("  --jg-selection-bg: ${colors.selectionBackground};")
        appendLine("  --jg-selection-fg: ${colors.selectionForeground};")
        appendLine("  --jg-error: ${colors.error};")
        appendLine("  --jg-warning: ${colors.warning};")
        appendLine("  --jg-success: ${colors.success};")
        appendLine("  --jg-info: ${colors.info};")
        appendLine("  --jg-grid-header-bg: ${dataGrid.headerBackground};")
        appendLine("  --jg-grid-header-fg: ${dataGrid.headerForeground};")
        appendLine("  --jg-grid-row-even: ${dataGrid.rowEvenBackground};")
        appendLine("  --jg-grid-row-odd: ${dataGrid.rowOddBackground};")
        appendLine("  --jg-grid-line: ${dataGrid.gridLineColor};")
        appendLine("  --jg-syntax-keyword: ${syntax.keyword};")
        appendLine("  --jg-syntax-string: ${syntax.string};")
        appendLine("  --jg-syntax-number: ${syntax.number};")
        appendLine("  --jg-syntax-comment: ${syntax.comment};")
        appendLine("  --jg-syntax-func: ${syntax.function};")
        appendLine("}")
    }

    companion object {
        /**
         * The official Solarized Light palette defined by Ethan Schoonover.
         */
        val SOLARIZED_LIGHT = JormungandrTheme(
            id = "solarized.light",
            name = "Solarized Light",
            isDark = false,
            colors = ColorTokens(
                background = "#FDF6E3",          // base3
                secondaryBackground = "#EEE8D5", // base2
                surface = "#FAF2DC",
                foreground = "#657B83",          // base00
                mutedForeground = "#93A1A1",     // base1
                border = "#E0D8C3",
                accent = "#268BD2",              // blue
                accentHover = "#2AA198",         // cyan
                selectionBackground = "#EEE8D5", // base2
                selectionForeground = "#586E75", // base01
                error = "#DC322F",               // red
                warning = "#CB4B16",             // orange
                success = "#859900",             // green
                info = "#268BD2"                 // blue
            ),
            dataGrid = DataGridThemeTokens(
                headerBackground = "#E8E2CF",
                headerForeground = "#586E75",
                rowEvenBackground = "#FDF6E3",
                rowOddBackground = "#F7F1DD",
                gridLineColor = "#E0D8C3",
                selectionBackground = "#E3DCBA",
                selectionForeground = "#073642"
            ),
            syntax = SyntaxThemeTokens(
                keyword = "#859900", // green
                string = "#2AA198",  // cyan
                number = "#D33682",  // magenta
                comment = "#93A1A1", // base1
                function = "#268BD2",// blue
                variable = "#B58900",// yellow
                type = "#CB4B16"     // orange
            ),
            chartPalette = listOf(
                "#268BD2", // blue
                "#2AA198", // cyan
                "#859900", // green
                "#B58900", // yellow
                "#CB4B16", // orange
                "#DC322F", // red
                "#D33682", // magenta
                "#6C71C4"  // violet
            )
        )
    }
}
