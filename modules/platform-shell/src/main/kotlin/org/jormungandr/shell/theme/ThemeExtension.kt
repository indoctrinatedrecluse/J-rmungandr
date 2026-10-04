package org.jormungandr.shell.theme

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jormungandr.core.extension.*
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.core.theme.ThemeChangeListener
import org.jormungandr.core.theme.ThemeManager
import java.util.concurrent.CopyOnWriteArrayList

private val LOG = logger<ThemeExtension>()

/**
 * Modular extension governing universal IDE and extension theming.
 *
 * Implements [JormungandrExtension] to participate in lifecycle governance,
 * and implements [ThemeManager] to provide theme tokens across all modules.
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
        JormungandrTheme.BUBBLEGUM_BARBIE.id to JormungandrTheme.BUBBLEGUM_BARBIE
    )

    private val _currentTheme = MutableStateFlow(JormungandrTheme.SOLARIZED_LIGHT)
    override val currentTheme: StateFlow<JormungandrTheme> = _currentTheme.asStateFlow()

    private val _availableThemes = MutableStateFlow(
        listOf(JormungandrTheme.SOLARIZED_LIGHT, JormungandrTheme.BUBBLEGUM_BARBIE)
    )
    override val availableThemes: StateFlow<List<JormungandrTheme>> = _availableThemes.asStateFlow()

    private val listeners = CopyOnWriteArrayList<ThemeChangeListener>()

    override suspend fun initialize(context: ExtensionContext) {
        this.context = context
        _state = ExtensionState.INITIALIZED
        LOG.info("ThemeExtension initialized with default theme: ${currentTheme.value.name}")
    }

    override suspend fun activate() {
        check(_state == ExtensionState.INITIALIZED || _state == ExtensionState.PAUSED) {
            "Cannot activate from state $_state"
        }
        _state = ExtensionState.ACTIVE
        applyTheme(JormungandrTheme.SOLARIZED_LIGHT.id)
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
        _state = ExtensionState.TERMINATED
    }

    override fun applyTheme(themeId: String): Result<Unit> {
        val theme = themeRegistry[themeId]
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

        return Result.success(Unit)
    }

    override fun registerTheme(theme: JormungandrTheme) {
        themeRegistry[theme.id] = theme
        _availableThemes.value = themeRegistry.values.toList()
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
            _state = ExtensionState.TERMINATED
        }
    }
}
