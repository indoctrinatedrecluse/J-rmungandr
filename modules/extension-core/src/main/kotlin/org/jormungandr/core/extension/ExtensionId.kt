package org.jormungandr.core.extension

/**
 * Strongly typed identifier for a Jörmungandr modular extension.
 */
@JvmInline
value class ExtensionId(val value: String) {
    override fun toString(): String = value
}
