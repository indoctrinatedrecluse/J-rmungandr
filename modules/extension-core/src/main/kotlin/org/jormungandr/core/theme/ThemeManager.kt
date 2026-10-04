package org.jormungandr.core.theme

import com.intellij.openapi.Disposable
import kotlinx.coroutines.flow.StateFlow

/**
 * Listener invoked when the global theme is switched.
 */
fun interface ThemeChangeListener {
    fun onThemeChanged(newTheme: JormungandrTheme)
}

/**
 * Service governing application-wide theme states and propagating theme tokens
 * uniformly across IDE UI components and all modular extensions.
 */
interface ThemeManager : Disposable {
    /** Currently active theme */
    val currentTheme: StateFlow<JormungandrTheme>

    /** List of all installed and registered themes */
    val availableThemes: StateFlow<List<JormungandrTheme>>

    /**
     * Applies a registered theme by its unique identifier.
     */
    fun applyTheme(themeId: String): Result<Unit>

    /**
     * Registers a new theme into the theme catalog.
     */
    fun registerTheme(theme: JormungandrTheme)

    /**
     * Adds a listener tied to an IntelliJ [Disposable] lifecycle.
     */
    fun addThemeChangeListener(parentDisposable: Disposable, listener: ThemeChangeListener)
}
