package org.jormungandr.shell.ui.about

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jormungandr.shell.icon.JormungandrIcons

/**
 * Action opening the Jörmungandr About Dialog.
 */
class AboutAction : AnAction("About Jörmungandr", "Show application info, author attribution, and license", JormungandrIcons.APP_ICON) {

    override fun actionPerformed(e: AnActionEvent) {
        val dialog = AboutDialog(e.project)
        dialog.show()
    }
}
