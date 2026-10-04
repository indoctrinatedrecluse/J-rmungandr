package org.jormungandr.shell.splash

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import java.net.URL
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * Animated Splash Screen for Jörmungandr IDE.
 *
 * Displays a seamless 3.0-second looping GIF featuring the World Serpent
 * emblem and Solarized Light aesthetics while the IDE platform and modular
 * extensions initialize in the background.
 *
 * Guarantees a minimum display duration of at least 3,000 milliseconds,
 * even when the background UI components complete initialization earlier.
 */
object JormungandrSplashScreen {

    private val LOG = Logger.getInstance(JormungandrSplashScreen::class.java)

    /** Mandatory minimum display duration in milliseconds */
    const val MIN_RUNTIME_MS: Long = 3000L

    private const val SPLASH_RESOURCE = "/splash/splash.gif"

    @Volatile
    private var splashWindow: JWindow? = null

    @Volatile
    var startTimeMs: Long = 0L
        private set

    @Volatile
    var isDisplayed: Boolean = false
        private set

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initializes and displays the splash screen window on the Swing Event Dispatch Thread.
     * Starts the clock for minimum display duration enforcement.
     */
    fun show() {
        startTimeMs = System.currentTimeMillis()
        isDisplayed = true

        if (GraphicsEnvironment.isHeadless()) {
            LOG.info("Headless environment detected; skipping Jörmungandr splash screen display.")
            return
        }

        SwingUtilities.invokeLater {
            try {
                if (splashWindow != null) return@invokeLater

                val splashUrl: URL? = JormungandrSplashScreen::class.java.getResource(SPLASH_RESOURCE)
                if (splashUrl == null) {
                    LOG.warn("Splash screen resource not found at $SPLASH_RESOURCE")
                    return@invokeLater
                }

                val imageIcon = ImageIcon(splashUrl)
                val window = JWindow().apply {
                    contentPane.layout = BorderLayout()
                    contentPane.add(JLabel(imageIcon), BorderLayout.CENTER)
                    background = Color(253, 246, 227) // Solarized Base3

                    // Apply Jörmungandr window icons
                    val icons = JormungandrIcons.getIconImages()
                    if (icons.isNotEmpty()) {
                        iconImages = icons
                    }

                    pack()
                    setLocationRelativeTo(null) // Center on screen
                    isAlwaysOnTop = true
                }

                splashWindow = window
                window.isVisible = true

                LOG.info("Jörmungandr animated splash screen displayed (MinRuntime: ${MIN_RUNTIME_MS}ms).")
            } catch (e: Exception) {
                LOG.error("Failed to display Jörmungandr splash screen", e)
            }
        }
    }

    /**
     * Calculates remaining time required to satisfy the minimum runtime threshold.
     *
     * @param now Current timestamp in milliseconds (defaults to System.currentTimeMillis())
     */
    fun calculateRemainingDelay(now: Long = System.currentTimeMillis()): Long {
        if (startTimeMs <= 0L) return 0L
        val elapsed = now - startTimeMs
        return (MIN_RUNTIME_MS - elapsed).coerceAtLeast(0L)
    }

    /**
     * Dismisses the splash screen after ensuring the minimum runtime (3 seconds) has elapsed.
     *
     * @param onDismissed Optional callback executed once the splash screen is fully disposed on the EDT.
     */
    fun dismiss(onDismissed: (() -> Unit)? = null) {
        val remainingDelay = calculateRemainingDelay()

        coroutineScope.launch {
            if (remainingDelay > 0L) {
                LOG.info("IDE UI loaded in background early. Holding splash screen for remaining ${remainingDelay}ms to satisfy 3s minimum display.")
                delay(remainingDelay)
            }

            SwingUtilities.invokeLater {
                try {
                    splashWindow?.let { win ->
                        win.isVisible = false
                        win.dispose()
                        splashWindow = null
                        isDisplayed = false
                        LOG.info("Jörmungandr splash screen dismissed.")
                    }
                } catch (e: Exception) {
                    LOG.warn("Error disposing splash screen window", e)
                } finally {
                    onDismissed?.invoke()
                }
            }
        }
    }

    /**
     * Immediately disposes the splash window without waiting (useful for test tear-downs).
     */
    fun forceClose() {
        SwingUtilities.invokeLater {
            splashWindow?.dispose()
            splashWindow = null
            isDisplayed = false
            startTimeMs = 0L
        }
    }
}
