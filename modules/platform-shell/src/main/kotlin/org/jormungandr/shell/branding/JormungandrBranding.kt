package org.jormungandr.shell.branding

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import org.jormungandr.shell.icon.JormungandrIcons
import org.jormungandr.shell.ui.about.AboutAction
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.lang.reflect.Field
import javax.swing.*

/**
 * Universal Branding & White-Labeling Engine for Jörmungandr IDE.
 *
 * Replaces upstream IntelliJ IDEA and JetBrains branding throughout the application:
 * 1. Mutates ApplicationNamesInfo (product name, full name, edition, script, motto).
 * 2. Mutates ApplicationInfoImpl (company name, company URL, copyright, logos, doc URLs).
 * 3. Enforces Jörmungandr multi-resolution window icons on title bars and taskbars.
 * 4. Intercepts and transforms window titles and UI labels across all frames, dialogs, and menus.
 * 5. Replaces upstream About action with Jörmungandr About action.
 */
object JormungandrBranding {

    private val LOG = Logger.getInstance(JormungandrBranding::class.java)

    const val PRODUCT_NAME = "Jörmungandr"
    const val VENDOR_NAME = "indoctrinatedrecluse"
    const val COMPANY_NAME = "Indoctrinated Recluse"
    const val COMPANY_URL = "https://github.com/indoctrinatedrecluse/Jormungandr"
    const val MOTTO = "The Modular IDE for Data Science & Analytics"

    private val REPLACEMENT_MAP = listOf(
        "Welcome to IntelliJ IDEA" to "Welcome to $PRODUCT_NAME",
        "IntelliJ IDEA Community Edition" to PRODUCT_NAME,
        "IntelliJ IDEA Ultimate" to PRODUCT_NAME,
        "IntelliJ IDEA" to PRODUCT_NAME,
        "JetBrains s.r.o." to COMPANY_NAME,
        "JetBrains" to PRODUCT_NAME,
        "IDEA" to PRODUCT_NAME
    )

    @Volatile
    private var isInitialized = false

    @Volatile
    private var awtEventListener: AWTEventListener? = null

    /**
     * Initializes the complete branding overhaul across the application runtime.
     */
    fun initialize() {
        if (isInitialized) return
        isInitialized = true

        LOG.info("Initializing Jörmungandr comprehensive white-label branding overhaul...")

        patchApplicationNamesInfo()
        patchApplicationInfo()
        replaceUpstreamAboutAction()
        installGlobalWindowInterceptor()
        rebrandAllWindows()

        LOG.info("Jörmungandr branding overhaul successfully initialized.")
    }

    /**
     * Reflectively modifies ApplicationNamesInfo instance to report Jörmungandr identity.
     */
    fun patchApplicationNamesInfo() {
        try {
            val namesClass = Class.forName("com.intellij.openapi.application.ApplicationNamesInfo")
            val instanceMethod = namesClass.getMethod("getInstance")
            val instance = instanceMethod.invoke(null) ?: return

            setFieldValue(namesClass, instance, "myProductName", PRODUCT_NAME)
            setFieldValue(namesClass, instance, "myFullProductName", PRODUCT_NAME)
            setFieldValue(namesClass, instance, "myEditionName", null)
            setFieldValue(namesClass, instance, "myScriptName", "jormungandr")
            setFieldValue(namesClass, instance, "myMotto", MOTTO)

            LOG.info("Successfully patched ApplicationNamesInfo to '$PRODUCT_NAME'.")
        } catch (e: Throwable) {
            LOG.warn("Could not patch ApplicationNamesInfo", e)
        }
    }

    /**
     * Reflectively modifies ApplicationInfoImpl instance to report Indoctrinated Recluse attribution,
     * logos, URLs, and copyright dates.
     */
    fun patchApplicationInfo() {
        try {
            val appInfoClass = Class.forName("com.intellij.openapi.application.impl.ApplicationInfoImpl")
            val getShadow = appInfoClass.getMethod("getShadowInstance")
            val instance = getShadow.invoke(null) ?: return

            setFieldValue(appInfoClass, instance, "myCompanyName", COMPANY_NAME)
            setFieldValue(appInfoClass, instance, "myShortCompanyName", COMPANY_NAME)
            setFieldValue(appInfoClass, instance, "myCompanyUrl", COMPANY_URL)
            setFieldValue(appInfoClass, instance, "myCopyrightStart", "2025")
            setFieldValue(appInfoClass, instance, "splashImageUrl", "/splash/splash.png")
            setFieldValue(appInfoClass, instance, "eapSplashImageUrl", "/splash/splash.png")
            setFieldValue(appInfoClass, instance, "svgIconUrl", "/icons/jormungandr.svg")
            setFieldValue(appInfoClass, instance, "mySvgEapIconUrl", "/icons/jormungandr.svg")
            setFieldValue(appInfoClass, instance, "mySmallSvgIconUrl", "/icons/jormungandr_16.svg")
            setFieldValue(appInfoClass, instance, "mySmallSvgEapIconUrl", "/icons/jormungandr_16.svg")
            setFieldValue(appInfoClass, instance, "myWelcomeScreenLogoUrl", "/icons/jormungandr_64.png")
            setFieldValue(appInfoClass, instance, "myProductUrl", COMPANY_URL)
            setFieldValue(appInfoClass, instance, "myDocumentationUrl", "$COMPANY_URL#readme")
            setFieldValue(appInfoClass, instance, "mySupportUrl", "$COMPANY_URL/issues")
            setFieldValue(appInfoClass, instance, "myFeedbackUrl", "$COMPANY_URL/issues")
            setFieldValue(appInfoClass, instance, "myYoutrackUrl", "$COMPANY_URL/issues")

            LOG.info("Successfully patched ApplicationInfoImpl to '$COMPANY_NAME' ($COMPANY_URL).")
        } catch (e: Throwable) {
            LOG.warn("Could not patch ApplicationInfoImpl", e)
        }
    }

    /**
     * Replaces upstream IntelliJ 'About' action with Jörmungandr's custom AboutAction in ActionManager.
     */
    fun replaceUpstreamAboutAction() {
        try {
            val actionManager = ActionManager.getInstance() ?: return
            val aboutAction = AboutAction()
            val existing = actionManager.getAction("About")
            if (existing != null && existing !is AboutAction) {
                actionManager.replaceAction("About", aboutAction)
                LOG.info("Replaced upstream 'About' action with Jörmungandr AboutAction.")
            }
        } catch (e: Throwable) {
            LOG.warn("Could not replace upstream About action (non-fatal)", e)
        }
    }

    /**
     * Installs a permanent AWT Event Listener that brands any existing or newly spawned
     * Window, Frame, Dialog, or popup component.
     */
    fun installGlobalWindowInterceptor() {
        if (GraphicsEnvironment.isHeadless()) return
        if (awtEventListener != null) return

        val listener = AWTEventListener { event ->
            if (event is WindowEvent && (event.id == WindowEvent.WINDOW_OPENED || event.id == WindowEvent.WINDOW_ACTIVATED)) {
                event.window?.let { win ->
                    brandWindow(win)
                }
            }
        }
        awtEventListener = listener
        Toolkit.getDefaultToolkit().addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        LOG.info("Registered global AWT window branding interceptor.")
    }

    /**
     * Brands a specific Window instance (title bar icon, title text, and child UI elements).
     */
    fun brandWindow(win: Window) {
        SwingUtilities.invokeLater {
            try {
                // 1. Enforce Jörmungandr multi-resolution window icon on title bar and taskbar
                val icons = JormungandrIcons.getIconImages()
                if (icons.isNotEmpty()) {
                    win.iconImages = icons
                }

                // 2. Rebrand title
                if (win is Frame) {
                    val currentTitle = win.title
                    if (currentTitle != null) {
                        val rebranded = rebrandText(currentTitle)
                        if (rebranded != currentTitle) {
                            win.title = rebranded
                        }
                    }
                } else if (win is Dialog) {
                    val currentTitle = win.title
                    if (currentTitle != null) {
                        val rebranded = rebrandText(currentTitle)
                        if (rebranded != currentTitle) {
                            win.title = rebranded
                        }
                    }
                }

                // 3. Rebrand child components
                rebrandComponents(win)
            } catch (e: Throwable) {
                LOG.warn("Could not brand window: ${win.javaClass.name}", e)
            }
        }
    }

    /**
     * Immediately applies branding to all existing Frames and Windows.
     */
    fun rebrandAllWindows() {
        if (GraphicsEnvironment.isHeadless()) return
        SwingUtilities.invokeLater {
            try {
                for (frame in Frame.getFrames()) {
                    brandWindow(frame)
                }
                for (window in Window.getWindows()) {
                    brandWindow(window)
                }
            } catch (e: Throwable) {
                LOG.warn("Could not rebrand all existing windows", e)
            }
        }
    }

    /**
     * Recursively traverses a component container to substitute upstream branding substrings.
     */
    fun rebrandComponents(container: Container) {
        for (comp in container.components) {
            when (comp) {
                is JLabel -> {
                    val text = comp.text
                    if (text != null) {
                        val rebranded = rebrandText(text)
                        if (rebranded != text) {
                            comp.text = rebranded
                        }
                    }
                }
                is AbstractButton -> {
                    val text = comp.text
                    if (text != null) {
                        val rebranded = rebrandText(text)
                        if (rebranded != text) {
                            comp.text = rebranded
                        }
                    }
                }
            }
            if (comp is Container && comp.componentCount > 0) {
                rebrandComponents(comp)
            }
        }
    }

    /**
     * Substitutes known upstream branding substrings with Jörmungandr equivalents.
     */
    fun rebrandText(text: String): String {
        var result = text
        for ((pattern, replacement) in REPLACEMENT_MAP) {
            if (result.contains(pattern)) {
                result = result.replace(pattern, replacement)
            }
        }
        return result
    }

    private fun setFieldValue(clazz: Class<*>, target: Any, fieldName: String, value: Any?) {
        try {
            val field: Field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(target, value)
        } catch (_: NoSuchFieldException) {
            try {
                for (f in clazz.declaredFields) {
                    if (f.name.equals(fieldName, ignoreCase = true)) {
                        f.isAccessible = true
                        f.set(target, value)
                        return
                    }
                }
            } catch (_: Throwable) {}
        } catch (e: Throwable) {
            LOG.warn("Could not set field '$fieldName' on ${clazz.name}", e)
        }
    }
}
