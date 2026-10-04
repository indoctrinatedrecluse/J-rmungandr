package org.jormungandr.shell.splash

import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.*
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.WindowEvent
import java.net.URL
import javax.swing.ImageIcon
import javax.swing.JLabel
import javax.swing.JWindow
import javax.swing.SwingUtilities

/**
 * Animated Splash Screen for Jörmungandr IDE.
 *
 * Displays a seamless looping GIF featuring the World Serpent emblem
 * and Solarized Light aesthetics while the IDE platform and modular
 * extensions initialize in the background.
 *
 * Display duration behavior:
 * Displays for at most 3,000 milliseconds OR until the IDE UI (Welcome Screen
 * or Project Frame) is loaded, whichever is faster.
 * Also dismisses immediately upon user click.
 */
object JormungandrSplashScreen {

    private val LOG = Logger.getInstance(JormungandrSplashScreen::class.java)

    /** Maximum display duration in milliseconds before auto-dismissal */
    const val MAX_RUNTIME_MS: Long = 3000L

    /** Backward-compatibility alias for tests and external callers */
    const val MIN_RUNTIME_MS: Long = MAX_RUNTIME_MS

    private const val SPLASH_RESOURCE = "/splash/splash.gif"

    @Volatile
    private var splashWindow: JWindow? = null

    @Volatile
    var startTimeMs: Long = 0L
        private set

    @Volatile
    var isDisplayed: Boolean = false
        private set

    @Volatile
    private var autoDismissJob: Job? = null

    @Volatile
    private var awtEventListener: AWTEventListener? = null

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initializes and displays the splash screen window on the Swing Event Dispatch Thread.
     * Enforces the "3000ms or UI ready, whichever is faster" lifecycle.
     */
    fun show() {
        startTimeMs = System.currentTimeMillis()
        isDisplayed = true

        if (GraphicsEnvironment.isHeadless()) {
            LOG.info("Headless environment detected; skipping Jörmungandr splash screen display.")
            return
        }

        // Auto-dismiss timeout: fires when 3,000ms threshold expires
        autoDismissJob?.cancel()
        autoDismissJob = coroutineScope.launch {
            delay(MAX_RUNTIME_MS)
            if (isDisplayed) {
                LOG.info("Splash screen reached max display duration (${MAX_RUNTIME_MS}ms). Auto-dismissing.")
                dismiss()
            }
        }

        SwingUtilities.invokeLater {
            try {
                if (splashWindow != null) return@invokeLater

                // If any IDE UI frame is already showing, dismiss immediately
                for (frame in Frame.getFrames()) {
                    if (frame !== splashWindow && frame.isShowing) {
                        LOG.info("Main IDE UI frame already visible (${frame.javaClass.simpleName}). Dismissing splash immediately.")
                        dismiss()
                        return@invokeLater
                    }
                }

                val splashUrl: URL? = JormungandrSplashScreen::class.java.getResource(SPLASH_RESOURCE)
                if (splashUrl == null) {
                    LOG.warn("Splash screen resource not found at $SPLASH_RESOURCE")
                    dismiss()
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

                    // Click anywhere to dismiss immediately
                    addMouseListener(object : MouseAdapter() {
                        override fun mouseClicked(e: MouseEvent) {
                            LOG.info("Splash screen clicked by user; dismissing immediately.")
                            dismiss()
                        }
                    })

                    pack()
                    setLocationRelativeTo(null) // Center on screen
                }

                splashWindow = window

                // Register global AWT listener to dismiss the instant any IDE UI window opens
                val listener = AWTEventListener { event ->
                    if (event is WindowEvent && event.id == WindowEvent.WINDOW_OPENED) {
                        val win = event.window
                        if (win != null && win !== splashWindow && win.isShowing) {
                            LOG.info("Detected main IDE window opened (${win.javaClass.simpleName}). Dismissing splash screen immediately.")
                            dismiss()
                        }
                    }
                }
                awtEventListener = listener
                Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)

                window.isVisible = true
                window.toFront()

                LOG.info("Jörmungandr animated splash screen displayed (MaxRuntime: ${MAX_RUNTIME_MS}ms or UI ready).")
            } catch (e: Exception) {
                LOG.error("Failed to display Jörmungandr splash screen", e)
            }
        }
    }

    /**
     * Calculates remaining time until auto-dismissal expires.
     *
     * @param now Current timestamp in milliseconds (defaults to System.currentTimeMillis())
     */
    fun calculateRemainingDelay(now: Long = System.currentTimeMillis()): Long {
        if (!isDisplayed || startTimeMs <= 0L) return 0L
        val elapsed = now - startTimeMs
        return (MAX_RUNTIME_MS - elapsed).coerceAtLeast(0L)
    }

    /**
     * Dismisses the splash screen immediately on the Swing Event Dispatch Thread without artificial holding delays.
     *
     * @param onDismissed Optional callback executed once the splash screen is fully disposed on the EDT.
     */
    fun dismiss(onDismissed: (() -> Unit)? = null) {
        autoDismissJob?.cancel()
        autoDismissJob = null

        removeAwtListener()
        isDisplayed = false

        SwingUtilities.invokeLater {
            try {
                splashWindow?.let { win ->
                    win.isVisible = false
                    win.dispose()
                    splashWindow = null
                    LOG.info("Jörmungandr splash screen dismissed.")
                }
            } catch (e: Exception) {
                LOG.warn("Error disposing splash screen window", e)
            } finally {
                onDismissed?.invoke()
            }
        }
    }

    private fun removeAwtListener() {
        awtEventListener?.let { listener ->
            try {
                Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
            } catch (e: Exception) {
                LOG.warn("Error removing AWT event listener", e)
            } finally {
                awtEventListener = null
            }
        }
    }

    /**
     * Immediately disposes the splash window without waiting (useful for test tear-downs).
     */
    fun forceClose() {
        autoDismissJob?.cancel()
        autoDismissJob = null
        removeAwtListener()
        isDisplayed = false
        startTimeMs = 0L

        val win = splashWindow
        splashWindow = null
        if (win != null) {
            if (SwingUtilities.isEventDispatchThread()) {
                win.dispose()
            } else {
                SwingUtilities.invokeLater {
                    win.dispose()
                }
            }
        }
    }
}
