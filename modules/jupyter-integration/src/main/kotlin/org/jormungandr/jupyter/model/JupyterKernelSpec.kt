package org.jormungandr.jupyter.model

/**
 * Representation of a Jupyter Kernel Specification (kernel.json).
 */
data class JupyterKernelSpec(
    val id: String,
    val displayName: String,
    val language: String,
    val argv: List<String>,
    val interruptMode: String = "signal",
    val env: Map<String, String> = emptyMap()
)
