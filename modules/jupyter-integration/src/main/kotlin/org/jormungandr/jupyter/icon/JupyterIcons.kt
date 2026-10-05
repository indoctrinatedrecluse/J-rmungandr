package org.jormungandr.jupyter.icon

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons for the Jupyter Integration subsystem.
 */
object JupyterIcons {
    @JvmField
    val NOTEBOOK_16: Icon = IconLoader.getIcon("/icons/jormungandr_16.png", JupyterIcons::class.java)

    @JvmField
    val RUN_16: Icon = IconLoader.getIcon("/icons/jormungandr_16.png", JupyterIcons::class.java)
}
