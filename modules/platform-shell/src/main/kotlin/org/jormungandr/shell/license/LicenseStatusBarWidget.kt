/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.shell.license

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import org.jormungandr.core.license.LicenseInfo
import org.jormungandr.core.license.LicenseService
import java.awt.Cursor
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Status bar widget displaying real-time visual license badge.
 * Clicking opens the LicenseDialog modal.
 */
class LicenseStatusBarWidget(private val project: Project) : CustomStatusBarWidget {

    private val licenseService = LicenseService.getInstance()
    private val panel = JPanel(FlowLayout(FlowLayout.CENTER, 4, 0)).apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                LicenseDialog(project).show()
            }
        })
    }

    private val listener: (LicenseInfo) -> Unit = { info ->
        SwingUtilities.invokeLater { updateWidget(info) }
    }

    override fun ID(): String = WIDGET_ID

    override fun getComponent(): JComponent = panel

    override fun install(statusBar: StatusBar) {
        licenseService.addListener(listener)
        updateWidget(licenseService.currentLicense.value)
    }

    private fun updateWidget(info: LicenseInfo) {
        panel.removeAll()
        val badge = LicenseBadgeFactory.createBadgeComponent(info, large = false)
        panel.add(badge)
        panel.toolTipText = "License: ${info.licenseType.displayName} (${info.status.displayName}) - Click to manage"
        panel.revalidate()
        panel.repaint()
    }

    override fun dispose() {
        licenseService.removeListener(listener)
    }

    companion object {
        const val WIDGET_ID = "org.jormungandr.shell.license.LicenseStatusBarWidget"
    }
}

/**
 * Factory registering the license badge on the status bar.
 */
class LicenseStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = LicenseStatusBarWidget.WIDGET_ID

    override fun getDisplayName(): String = "Jörmungandr Commercial License Tier"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = LicenseStatusBarWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}
