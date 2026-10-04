package org.jormungandr.shell.ui.theme

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import org.jormungandr.shell.icon.JormungandrIcons

/**
 * Action opening the Jörmungandr Themes modal from the View menu.
 */
class ThemesAction : AnAction("Themes...", "View palette swatches and switch active UI themes", JormungandrIcons.APP_ICON) {

    override fun actionPerformed(e: AnActionEvent) {
        val dialog = ThemesDialog(e.project)
        dialog.show()
    }
}
