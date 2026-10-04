package org.jormungandr.shell.theme

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.LafManagerListener
import com.intellij.ide.ui.laf.UIThemeLookAndFeelInfo
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.core.extension.*
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.core.theme.ThemeChangeListener
import org.jormungandr.core.theme.ThemeManager
import java.awt.Window
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.SwingUtilities

private val LOG = logger<ThemeExtension>()

/**
 * Modular extension governing universal IDE and extension theming.
 *
 * Implements [JormungandrExtension] to participate in lifecycle governance,
 * and implements [ThemeManager] to provide theme tokens across all modules.
 * Directly integrates with IntelliJ Platform [LafManager] and [EditorColorsManager]
 * to apply Look & Feel, UI chrome styling, and syntax highlighting in real time.
 */
@Service(Service.Level.APP)
class ThemeExtension : JormungandrExtension, ThemeManager {

    override val id: ExtensionId = ExtensionId("org.jormungandr.theme")

    override val metadata: ExtensionMetadata = ExtensionMetadata(
        id = id,
        displayName = "Theme & Visual Styling Engine",
        version = "0.1.0",
        description = "Provides universal theme token distribution and styling propagation across all UI elements and extensions.",
        author = "indoctrinatedrecluse",
        supportedLanguages = listOf("CSS", "JSON", "XML"),
        associatedStacks = listOf("IntelliJ LookAndFeel", "JCEF WebViews", "Solarized Light", "Bubblegum Barbie"),
        quota = ResourceQuota(
            maxHeapBytes = 64L * 1024 * 1024,
            maxOffHeapBytes = 0L,
            maxWorkerThreads = 2
        )
    )

    private var _state: ExtensionState = ExtensionState.UNLOADED
    override val state: ExtensionState get() = _state

    private var context: ExtensionContext? = null

    private val themeRegistry = mutableMapOf<String, JormungandrTheme>(
        JormungandrTheme.SOLARIZED_LIGHT.id to JormungandrTheme.SOLARIZED_LIGHT,
        "org.jormungandr.theme.${JormungandrTheme.SOLARIZED_LIGHT.id}" to JormungandrTheme.SOLARIZED_LIGHT,
        JormungandrTheme.BUBBLEGUM_BARBIE.id to JormungandrTheme.BUBBLEGUM_BARBIE,
        "org.jormungandr.theme.${JormungandrTheme.BUBBLEGUM_BARBIE.id}" to JormungandrTheme.BUBBLEGUM_BARBIE
    )

    private val _currentTheme = MutableStateFlow(JormungandrTheme.SOLARIZED_LIGHT)
    override val currentTheme: StateFlow<JormungandrTheme> = _currentTheme.asStateFlow()

    private val _availableThemes = MutableStateFlow(
        listOf(JormungandrTheme.SOLARIZED_LIGHT, JormungandrTheme.BUBBLEGUM_BARBIE)
    )
    override val availableThemes: StateFlow<List<JormungandrTheme>> = _availableThemes.asStateFlow()

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()
    private var messageBusDisposable: Disposable? = null

    init {
        // Synchronize initial state if running inside IntelliJ Application
        runCatching {
            val app = ApplicationManager.getApplication()
            if (app != null && !app.isDisposed) {
                setupLafManagerListener()
                val currentLaf = runCatching { LafManager.getInstance()?.currentUIThemeLookAndFeel }.getOrNull()
                if (currentLaf != null) {
                    val matched = findThemeInRegistry(currentLaf.id) ?: findThemeInRegistry(currentLaf.name)
                    if (matched != null) {
                        _currentTheme.value = matched
                    }
                }
            }
        }
    }

    override suspend fun initialize(context: ExtensionContext) {
        this.context = context
        _state = ExtensionState.INITIALIZED
        setupLafManagerListener()
        LOG.info("ThemeExtension initialized with default theme: ${currentTheme.value.name}")
    }

    override suspend fun activate() {
        check(_state == ExtensionState.INITIALIZED || _state == ExtensionState.PAUSED) {
            "Cannot activate from state $_state"
        }
        _state = ExtensionState.ACTIVE
        setupLafManagerListener()

        // Determine if LafManager already has a theme active
        val currentLaf = runCatching { LafManager.getInstance()?.currentUIThemeLookAndFeel }.getOrNull()
        val matchedTheme = currentLaf?.let { laf ->
            findThemeInRegistry(laf.id) ?: findThemeInRegistry(laf.name)
        }
        val targetThemeId = matchedTheme?.id ?: JormungandrTheme.SOLARIZED_LIGHT.id

        applyTheme(targetThemeId)
        LOG.info("ThemeExtension active. Initial theme applied: ${currentTheme.value.name}")
    }

    override suspend fun pause() {
        _state = ExtensionState.PAUSED
    }

    override suspend fun resume() {
        _state = ExtensionState.ACTIVE
    }

    override suspend fun trimMemory(level: MemoryPressureLevel) {
        // Theme tokens have minimal footprint; no-op
    }

    override suspend fun deactivate() {
        _state = ExtensionState.DISPOSING
        listeners.clear()
        messageBusDisposable?.let { Disposer.dispose(it) }
        messageBusDisposable = null
        _state = ExtensionState.TERMINATED
    }

    override fun applyTheme(themeId: String): Result<Unit> {
        val theme = findThemeInRegistry(themeId)
            ?: return Result.failure(IllegalArgumentException("Theme not found with id: $themeId"))

        _currentTheme.value = theme
        LOG.info("Applying global theme: ${theme.name} (Dark=${theme.isDark})")

        // Broadcast to all active UI component listeners and extensions
        for (listener in listeners) {
            runCatching {
                listener.onThemeChanged(theme)
            }.onFailure { err ->
                LOG.error("Failed to propagate theme change to listener", err)
            }
        }

        // Bridge to IntelliJ Platform LafManager & EditorColorsManager if running within IntelliJ Application
        applyToIntelliJPlatform(theme)

        return Result.success(Unit)
    }

    private fun findThemeInRegistry(key: String?): JormungandrTheme? {
        if (key == null) return null
        return themeRegistry[key]
            ?: themeRegistry.values.firstOrNull { t ->
                t.id.equals(key, ignoreCase = true) ||
                t.name.equals(key, ignoreCase = true) ||
                key.endsWith(t.id, ignoreCase = true) ||
                key.contains(t.name, ignoreCase = true)
            }
    }

    private fun applyToIntelliJPlatform(theme: JormungandrTheme) {
        val app = ApplicationManager.getApplication() ?: return
        if (app.isDisposed) return

        val applyAction = Runnable {
            runCatching {
                val lafManager = LafManager.getInstance() ?: return@runCatching
                val targetLaf = findIntelliJTheme(lafManager, theme)

                if (targetLaf != null) {
                    val currentLaf = lafManager.currentUIThemeLookAndFeel
                    if (currentLaf?.id != targetLaf.id) {
                        LOG.info("Found matching IntelliJ UITheme: ${targetLaf.name} (${targetLaf.id}), applying LookAndFeel")
                        // false for lockEditorScheme installs the editor color scheme configured in theme.json
                        lafManager.setCurrentLookAndFeel(targetLaf, false)
                        lafManager.updateUI()
                        lafManager.repaintUI()
                    }

                    // Also explicitly ensure EditorColorsManager applies the editor scheme
                    applyEditorScheme(targetLaf, theme)

                    // Repaint all active frames and windows
                    for (window in Window.getWindows()) {
                        runCatching {
                            SwingUtilities.updateComponentTreeUI(window)
                            window.repaint()
                        }
                    }
                } else {
                    LOG.warn("Could not find IntelliJ UITheme matching ${theme.name} (${theme.id})")
                }
            }.onFailure { err ->
                LOG.warn("Failed to apply LookAndFeel via LafManager: ${err.message}", err)
            }
        }

        if (app.isDispatchThread) {
            applyAction.run()
        } else {
            app.invokeLater(applyAction)
        }
    }

    private fun findIntelliJTheme(lafManager: LafManager, theme: JormungandrTheme): UIThemeLookAndFeelInfo? {
        return runCatching {
            lafManager.installedThemes.firstOrNull { info ->
                info.id.equals(theme.id, ignoreCase = true) ||
                info.id.equals("org.jormungandr.theme.${theme.id}", ignoreCase = true) ||
                info.name.equals(theme.name, ignoreCase = true) ||
                info.id.endsWith(theme.id, ignoreCase = true)
            } ?: lafManager.findLaf("org.jormungandr.theme.${theme.id}") ?: lafManager.findLaf(theme.id)
        }.getOrNull()
    }

    private fun applyEditorScheme(lafInfo: UIThemeLookAndFeelInfo, theme: JormungandrTheme) {
        runCatching {
            val colorsManager = EditorColorsManager.getInstance() ?: return@runCatching
            val schemeName = lafInfo.editorSchemeId ?: theme.name
            val scheme = colorsManager.getScheme(schemeName)
                ?: colorsManager.getScheme(theme.name)
                ?: colorsManager.allSchemes.firstOrNull { it.name.equals(theme.name, ignoreCase = true) }

            if (scheme != null && colorsManager.globalScheme.name != scheme.name) {
                LOG.info("Applying EditorColorsScheme: ${scheme.name}")
                colorsManager.setGlobalScheme(scheme)
            }
        }.onFailure { err ->
            LOG.warn("Could not apply editor color scheme for ${theme.name}", err)
        }
    }

    private fun setupLafManagerListener() {
        val app = ApplicationManager.getApplication() ?: return
        if (app.isDisposed || messageBusDisposable != null) return

        runCatching {
            val disposable = Disposer.newDisposable("ThemeExtension.LafManagerListener")
            messageBusDisposable = disposable

            app.messageBus.connect(disposable).subscribe(LafManagerListener.TOPIC, LafManagerListener { lafManager ->
                val currentLaf = lafManager.currentUIThemeLookAndFeel ?: return@LafManagerListener
                val matched = findThemeInRegistry(currentLaf.id) ?: findThemeInRegistry(currentLaf.name)
                if (matched != null && _currentTheme.value.id != matched.id) {
                    LOG.info("LafManager changed to ${matched.name}; synchronizing ThemeExtension state")
                    _currentTheme.value = matched
                    for (listener in listeners) {
                        runCatching { listener.onThemeChanged(matched) }
                    }
                }
            })
        }.onFailure { err ->
            LOG.warn("Could not setup LafManagerListener", err)
        }
    }

    override fun registerTheme(theme: JormungandrTheme) {
        themeRegistry[theme.id] = theme
        themeRegistry["org.jormungandr.theme.${theme.id}"] = theme
        _availableThemes.value = themeRegistry.values.distinctBy { it.id }
        LOG.info("Registered theme: ${theme.name} (${theme.id})")
    }

    override fun addThemeChangeListener(parentDisposable: Disposable, listener: ThemeChangeListener) {
        listeners.add(listener)
        Disposer.register(parentDisposable) {
            listeners.remove(listener)
        }
    }

    override fun dispose() {
        if (_state != ExtensionState.TERMINATED) {
            listeners.clear()
            messageBusDisposable?.let { Disposer.dispose(it) }
            messageBusDisposable = null
            _state = ExtensionState.TERMINATED
        }
    }
}
