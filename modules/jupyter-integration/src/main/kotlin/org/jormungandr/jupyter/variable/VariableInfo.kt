package org.jormungandr.jupyter.variable

/**
 * Metadata snapshot for an active variable in the live Python notebook workspace.
 */
data class VariableInfo(
    val name: String,
    val typeName: String,
    val shape: String = "-",
    val sizeBytes: Long = 0L,
    val sizeFormatted: String = "-",
    val preview: String = "",
    val isDataFrame: Boolean = false
) {
    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes < 0) return "-"
            if (bytes == 0L) return "0 B"
            val kb = bytes / 1024.0
            if (kb < 1.0) return "$bytes B"
            val mb = kb / 1024.0
            if (mb < 1.0) return String.format(java.util.Locale.US, "%.1f KB", kb)
            val gb = mb / 1024.0
            if (gb < 1.0) return String.format(java.util.Locale.US, "%.1f MB", mb)
            return String.format(java.util.Locale.US, "%.1f GB", gb)
        }
    }
}
