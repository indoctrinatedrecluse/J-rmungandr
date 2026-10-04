package org.jormungandr.shell.branding

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.diagnostic.Logger
import org.jormungandr.shell.icon.JormungandrIcons
import org.jormungandr.shell.ui.about.AboutAction
import java.awt.*
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.lang.reflect.Field
import java.lang.reflect.Proxy
import javax.swing.*

/**
 * Universal Branding & White-Labeling Engine for Jörmungandr IDE.
 *
 * Replaces upstream IntelliJ IDEA and JetBrains branding throughout the application:
 * 1. Mutates ApplicationNamesInfo (product name, full name, edition, script, motto).
 * 2. Mutates ApplicationInfoImpl (company name, company URL, copyright, logos, doc URLs).
 * 3. Intercepts and shields ConsentOptions against NPE on white-labeled vendor configurations.
 * 4. Enforces Jörmungandr multi-resolution window icons on title bars and taskbars.
 * 5. Intercepts and transforms window titles and UI labels across all frames, dialogs, and menus.
 * 6. Replaces upstream About action with Jörmungandr About action.
 * 7. Rebrands ActionManager action template presentations and descriptions.
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
        "IntelliJ IDEA Ultimate Edition" to PRODUCT_NAME,
        "IntelliJ IDEA Ultimate" to PRODUCT_NAME,
        "IntelliJ IDEA" to PRODUCT_NAME,
        "IntelliJ Platform" to "$PRODUCT_NAME Core Platform",
        "IntelliJ" to PRODUCT_NAME,
        "JetBrains s.r.o." to COMPANY_NAME,
        "JetBrains Community" to "$PRODUCT_NAME Community",
        "JetBrains Account" to "$PRODUCT_NAME Account",
        "JetBrains Marketplace" to "$PRODUCT_NAME Plugin Registry",
        "JetBrains Privacy Policy" to "$PRODUCT_NAME Privacy Policy",
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
        patchConsentOptions()
        replaceUpstreamAboutAction()
        rebrandActionManager()
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
     * Reflectively guards and intercepts ConsentOptions to guarantee that GDPR and usage statistics
     * consent queries never throw NullPointerException on custom/white-labeled vendor builds.
     */
    fun patchConsentOptions() {
        try {
            val consentOptionsClass = Class.forName("com.intellij.ide.gdpr.ConsentOptions")
            val getInstanceMethod = consentOptionsClass.getMethod("getInstance")
            val instance = getInstanceMethod.invoke(null) ?: return

            val backendField = consentOptionsClass.getDeclaredField("myBackend").apply { isAccessible = true }
            val originalBackend = backendField.get(instance)

            val backendInterface = Class.forName("com.intellij.ide.gdpr.ConsentOptions\$IOBackend")
            val fallbackJson = """[{"consentId":"rsch.send.usage.stat","version":"1.1","text":"Help improve Jörmungandr by sending anonymous usage statistics and diagnostics.","printableName":"Send Usage Statistics","accepted":"false"},{"consentId":"eap","version":"2021.2","text":"Send feedback requests and survey participation.","printableName":"Send feedback requests and surveys","accepted":"false"}]"""
            val fallbackConfirmed = "rsch.send.usage.stat:1.1:0:${System.currentTimeMillis()};eap:2021.2:0:${System.currentTimeMillis()};"

            val proxyBackend = Proxy.newProxyInstance(
                backendInterface.classLoader,
                arrayOf(backendInterface)
            ) { _, method, args ->
                when (method.name) {
                    "readBundledConsents", "readDefaultConsents" -> {
                        val res = try {
                            if (originalBackend != null) method.invoke(originalBackend, *(args ?: emptyArray())) as? String else null
                        } catch (_: Throwable) { null }
                        if (res.isNullOrBlank()) fallbackJson else res
                    }
                    "readConfirmedConsents" -> {
                        val res = try {
                            if (originalBackend != null) method.invoke(originalBackend, *(args ?: emptyArray())) as? String else null
                        } catch (_: Throwable) { null }
                        if (res.isNullOrBlank()) fallbackConfirmed else res
                    }
                    else -> {
                        if (originalBackend != null) {
                            try {
                                method.invoke(originalBackend, *(args ?: emptyArray()))
                            } catch (_: Throwable) { null }
                        } else null
                    }
                }
            }

            backendField.set(instance, proxyBackend)
            LOG.info("Successfully patched ConsentOptions IOBackend with resilient fallback provider.")
        } catch (e: Throwable) {
            LOG.warn("Could not patch ConsentOptions IOBackend (non-fatal)", e)
        }
    }

    /**
     * Inspects all registered actions in ActionManager and replaces IntelliJ/JetBrains
     * substrings in their template text and description.
     */
    fun rebrandActionManager() {
        try {
            val actionManager = ActionManager.getInstance() ?: return
            val actionIds = actionManager.getActionIdList("")
            for (id in actionIds) {
                val action = actionManager.getAction(id) ?: continue
                val text = action.templatePresentation.text
                if (text != null) {
                    val rebranded = rebrandText(text)
                    if (rebranded != text) {
                        action.templatePresentation.setText(rebranded)
                    }
                }
                val desc = action.templatePresentation.description
                if (desc != null) {
                    val rebranded = rebrandText(desc)
                    if (rebranded != desc) {
                        action.templatePresentation.description = rebranded
                    }
                }
            }
            LOG.info("Successfully rebranded ActionManager action presentations (${actionIds.size} inspected).")
        } catch (e: Throwable) {
            LOG.warn("Could not rebrand ActionManager actions (non-fatal)", e)
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
        // Inspect for CustomHeader
        if (container.javaClass.name.contains("CustomHeader")) {
            patchCustomHeader(container)
        }

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
                    val accessibleName = comp.accessibleContext?.accessibleName
                    if (accessibleName == "Application icon" ||
                        comp.javaClass.name.contains("CustomHeader") ||
                        comp.javaClass.name.contains("createProductIcon") ||
                        comp.name == "productIcon") {
                        comp.icon = JormungandrIcons.APP_ICON
                        comp.repaint()
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
                is JTabbedPane -> {
                    for (i in 0 until comp.tabCount) {
                        val title = comp.getTitleAt(i)
                        if (title != null) {
                            val rebranded = rebrandText(title)
                            if (rebranded != title) {
                                comp.setTitleAt(i, rebranded)
                            }
                        }
                    }
                }
                is JToolTip -> {
                    val tip = comp.tipText
                    if (tip != null) {
                        val rebranded = rebrandText(tip)
                        if (rebranded != tip) {
                            comp.tipText = rebranded
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
     * Reflectively updates the iconProvider of IntelliJ's CustomHeader
     * to provide the Jörmungandr World Serpent icon.
     */
    private fun patchCustomHeader(customHeader: Container) {
        try {
            val field = customHeader.javaClass.getDeclaredField("iconProvider").apply { isAccessible = true }
            val scaleContextCacheClass = Class.forName("com.intellij.ui.scale.ScaleContextCache")
            val constructor = scaleContextCacheClass.getConstructor(kotlin.jvm.functions.Function1::class.java)
            val lambda: (Any?) -> Icon = { JormungandrIcons.APP_ICON }
            val provider = constructor.newInstance(lambda)
            field.set(customHeader, provider)
            customHeader.repaint()
        } catch (_: Throwable) {
            // Non-fatal if CustomHeader layout differs
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
