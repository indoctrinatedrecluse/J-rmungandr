package org.jormungandr.shell.ui.extensions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jormungandr.shell.icon.JormungandrIcons

/**
 * Action opening the Jörmungandr Extensions modal from the View menu.
 */
class ExtensionsAction : AnAction("Extensions...", "Manage active modular extensions, language stacks, and quotas", JormungandrIcons.APP_ICON) {

    override fun actionPerformed(e: AnActionEvent) {
        val dialog = ExtensionsDialog(e.project)
        dialog.show()
    }
}
