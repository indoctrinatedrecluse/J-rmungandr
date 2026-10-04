package org.jormungandr.shell.splash

import com.intellij.ide.ApplicationInitializedListener
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.Frame
import javax.swing.SwingUtilities

import com.intellij.openapi.application.ApplicationManager
import org.jormungandr.core.extension.ExtensionManager
import org.jormungandr.core.theme.ThemeManager
import org.jormungandr.shell.extension.CoreExtensionsRegistry

/**
 * Early application bootstrap listener.
 * Launches the animated Solarized splash screen immediately as IntelliJ starts up,
 * and initializes core extensions in ExtensionManager.
 */
class JormungandrAppInitializer : ApplicationInitializedListener {

    private val LOG = Logger.getInstance(JormungandrAppInitializer::class.java)

    override suspend fun execute() {
        LOG.info("Jörmungandr application initializing; launching animated splash screen.")
        org.jormungandr.shell.registry.JormungandrRegistry.initialize()
        org.jormungandr.shell.branding.JormungandrBranding.initialize()
        JormungandrSplashScreen.show()

        val extensionManager = ApplicationManager.getApplication().getService(ExtensionManager::class.java)
        if (extensionManager != null) {
            CoreExtensionsRegistry.ensureCoreExtensionsRegistered(extensionManager)
        }

        val themeManager = ApplicationManager.getApplication().getService(ThemeManager::class.java)
        if (themeManager != null) {
            themeManager.applyTheme(themeManager.currentTheme.value.id)
        }
    }
}

/**
 * Post-startup activity executed when the project workspace and main UI frames are prepared.
 *
 * 1. Sets the window icon on the title bar and OS frame to the Jörmungandr World Serpent.
 * 2. Signals dismissal to the splash screen, which holds until the 3-second minimum display time has elapsed.
 */
class JormungandrStartupActivity : ProjectActivity {

    private val LOG = Logger.getInstance(JormungandrStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        LOG.info("Jörmungandr main UI loaded for project: ${project.name}. Applying window branding.")

        // Ensure active theme styling is applied to all newly opened project windows
        val themeManager = ApplicationManager.getApplication().getService(ThemeManager::class.java)
        if (themeManager != null) {
            themeManager.applyTheme(themeManager.currentTheme.value.id)
        }

        // Apply World Serpent branding to all active window title bars and OS taskbar frames
        applyAppIconBranding(project)

        // Dismiss splash screen (enforcing >= 3,000ms minimum display threshold)
        JormungandrSplashScreen.dismiss {
            LOG.info("Splash screen dismissed; main window active.")
        }
    }

    private fun applyAppIconBranding(project: Project) {
        SwingUtilities.invokeLater {
            try {
                val projectFrame = WindowManager.getInstance().getFrame(project)
                if (projectFrame != null) {
                    org.jormungandr.shell.branding.JormungandrBranding.brandWindow(projectFrame)
                }
                org.jormungandr.shell.branding.JormungandrBranding.rebrandAllWindows()
                LOG.info("Applied Jörmungandr World Serpent window icon and branding across all active frames.")
            } catch (e: Exception) {
                LOG.warn("Could not apply window icon branding", e)
            }
        }
    }
}
