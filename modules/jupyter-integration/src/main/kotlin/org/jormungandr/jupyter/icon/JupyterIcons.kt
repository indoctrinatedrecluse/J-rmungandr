package org.jormungandr.jupyter.icon

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Icons for the Jupyter Integration subsystem.
 */
object JupyterIcons {
    @JvmField
    val JUPYTER_16: Icon = IconLoader.getIcon("/icons/jupyter_16.png", JupyterIcons::class.java)

    @JvmField
    val PLOTS_16: Icon = IconLoader.getIcon("/icons/plots_16.png", JupyterIcons::class.java)

    @JvmField
    val NOTEBOOK_16: Icon = IconLoader.getIcon("/icons/jupyter_16.png", JupyterIcons::class.java)

    @JvmField
    val RUN_16: Icon = IconLoader.getIcon("/icons/jupyter_16.png", JupyterIcons::class.java)
}
