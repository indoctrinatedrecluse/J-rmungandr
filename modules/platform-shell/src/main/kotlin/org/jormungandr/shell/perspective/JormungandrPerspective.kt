package org.jormungandr.shell.perspective

/**
 * High-level workspace layout perspectives in Jörmungandr.
 */
enum class JormungandrPerspective(val displayName: String, val description: String) {
    /** Focused on notebook editing, variable inspection, and interactive plots */
    ANALYSIS("Analysis Mode", "Interactive notebooks, dataframes, and charts"),

    /** Focused on relational/NoSQL query consoles, schemas, and result tables */
    DATABASE("Database Mode", "SQL/NoSQL query consoles and schema explorer"),

    /** Focused on traditional Python script/module development, testing, and debugging */
    CODE("Code Mode", "Traditional IDE layout for engineering and debugging")
}
