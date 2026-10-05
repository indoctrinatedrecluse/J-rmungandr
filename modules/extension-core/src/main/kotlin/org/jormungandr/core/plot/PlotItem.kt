package org.jormungandr.core.plot

import java.awt.image.BufferedImage
import java.util.UUID

/**
 * Encapsulates a graphical plot/visualization artifact generated in Jörmungandr.
 */
data class PlotItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val source: String, // "Matplotlib", "Seaborn", "DataFrame Studio", "Plotly", etc.
    val timestamp: Long = System.currentTimeMillis(),
    val image: BufferedImage,
    val metadata: Map<String, String> = emptyMap()
) {
    val width: Int get() = image.width
    val height: Int get() = image.height
}
