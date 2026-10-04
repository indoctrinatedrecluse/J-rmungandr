package org.jormungandr.shell.branding

import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Frame
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

class JormungandrBrandingTest {

    @Test
    fun `test rebrandText replaces all known IntelliJ and JetBrains strings`() {
        assertEquals(
            "Welcome to Jörmungandr",
            JormungandrBranding.rebrandText("Welcome to IntelliJ IDEA")
        )
        assertEquals(
            "Jörmungandr",
            JormungandrBranding.rebrandText("IntelliJ IDEA Community Edition")
        )
        assertEquals(
            "About Jörmungandr",
            JormungandrBranding.rebrandText("About IntelliJ IDEA")
        )
        assertEquals(
            "Indoctrinated Recluse",
            JormungandrBranding.rebrandText("JetBrains s.r.o.")
        )
        assertEquals(
            "Jörmungandr Data Science",
            JormungandrBranding.rebrandText("JetBrains Data Science")
        )
    }

    @Test
    fun `test patchApplicationNamesInfo reflectively updates product identity`() {
        JormungandrBranding.patchApplicationNamesInfo()

        val namesInfo = ApplicationNamesInfo.getInstance()
        assertEquals("Jörmungandr", namesInfo.productName)
        assertEquals("Jörmungandr", namesInfo.fullProductName)
        assertEquals("jormungandr", namesInfo.scriptName)
    }

    @Test
    fun `test patchApplicationInfo reflectively updates company metadata`() {
        JormungandrBranding.patchApplicationInfo()

        val appInfo = ApplicationInfoImpl.getShadowInstance()
        assertEquals("Indoctrinated Recluse", appInfo.companyName)
        assertEquals("Indoctrinated Recluse", appInfo.shortCompanyName)
        assertEquals("/icons/jormungandr.svg", appInfo.applicationSvgIconUrl)
        assertEquals("/icons/jormungandr_16.svg", appInfo.smallApplicationSvgIconUrl)
    }

    @Test
    fun `test brandWindow updates frame title, icons, and child components`() {
        val frame = JFrame("Welcome to IntelliJ IDEA")
        val panel = JPanel()
        val label1 = JLabel("Welcome to IntelliJ IDEA")
        val label2 = JLabel("Copyright JetBrains s.r.o.")
        panel.add(label1)
        panel.add(label2)
        frame.contentPane.add(panel)

        // Run brandWindow synchronously on EDT
        SwingUtilities.invokeAndWait {
            JormungandrBranding.brandWindow(frame)
        }

        // Wait for nested invokeLater to complete
        SwingUtilities.invokeAndWait {
            assertEquals("Welcome to Jörmungandr", frame.title)
            assertFalse(frame.iconImages.isEmpty(), "Frame iconImages must be populated with Jörmungandr icons")
            assertEquals("Welcome to Jörmungandr", label1.text)
            assertEquals("Copyright Indoctrinated Recluse", label2.text)
        }
    }
}
