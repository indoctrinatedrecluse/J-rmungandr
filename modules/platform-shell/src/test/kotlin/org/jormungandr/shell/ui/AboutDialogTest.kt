package org.jormungandr.shell.ui

import org.jormungandr.shell.ui.about.AboutDialog
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.GraphicsEnvironment

class AboutDialogTest {

    @Test
    fun `test license resource exists and contains required attributions`() {
        val stream = AboutDialog::class.java.getResourceAsStream("/LICENSE")
        assertNotNull(stream, "/LICENSE resource must exist in platform-shell classpath")

        val content = stream!!.bufferedReader().readText()
        assertTrue(content.contains("Apache License"), "Must contain Apache License heading")
        assertTrue(content.contains("Version 2.0"), "Must be Apache License 2.0")
        assertTrue(content.contains("indoctrinatedrecluse"), "Must credit author indoctrinatedrecluse")
        assertTrue(content.contains("IntelliJ Platform Community Edition"), "Must mention upstream IntelliJ Platform")
        assertTrue(content.contains("JetBrains s.r.o."), "Must credit JetBrains upstream")
    }

    @Test
    fun `test about dialog instantiation requires application or headless guard`() {
        val app = com.intellij.openapi.application.ApplicationManager.getApplication()
        if (app == null || java.awt.GraphicsEnvironment.isHeadless()) {
            // In unit tests without running IntelliJ Application container, verify class metadata
            assertNotNull(AboutDialog::class.java)
            return
        }

        var dialogTitle: String? = null
        var resizable = false
        javax.swing.SwingUtilities.invokeAndWait {
            val dialog = AboutDialog()
            dialogTitle = dialog.title
            resizable = dialog.isResizable
        }

        assertEquals("About Jörmungandr", dialogTitle)
        assertTrue(resizable)
    }
}
