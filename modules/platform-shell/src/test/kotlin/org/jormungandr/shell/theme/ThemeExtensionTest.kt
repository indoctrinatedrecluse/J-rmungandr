package org.jormungandr.shell.theme

import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jormungandr.core.extension.ExtensionState
import org.jormungandr.core.theme.JormungandrTheme
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThemeExtensionTest {

    @Test
    fun `test solarized light is default theme`() {
        val themeExtension = ThemeExtension()
        val current = themeExtension.currentTheme.value

        assertEquals("solarized.light", current.id)
        assertEquals("Solarized Light", current.name)
        assertFalse(current.isDark)
        assertEquals("#FDF6E3", current.colors.background)
        assertEquals("#657B83", current.colors.foreground)
        assertEquals("#268BD2", current.colors.accent)
    }

    @Test
    fun `test theme change notification and disposal`() = runTest {
        val themeExtension = ThemeExtension()
        val disposable = Disposer.newDisposable()

        var notifiedTheme: JormungandrTheme? = null
        themeExtension.addThemeChangeListener(disposable) { theme ->
            notifiedTheme = theme
        }

        // Re-applying Solarized Light triggers callback
        themeExtension.applyTheme("solarized.light")
        assertNotNull(notifiedTheme)
        assertEquals("Solarized Light", notifiedTheme?.name)

        // Clean up disposable
        Disposer.dispose(disposable)
    }

    @Test
    fun `test css variables generation for solarized light`() {
        val css = JormungandrTheme.SOLARIZED_LIGHT.toCssVariables()

        assertTrue(css.contains("--jg-bg: #FDF6E3;"))
        assertTrue(css.contains("--jg-fg: #657B83;"))
        assertTrue(css.contains("--jg-accent: #268BD2;"))
        assertTrue(css.contains("--jg-grid-header-bg: #E8E2CF;"))
        assertTrue(css.contains("--jg-syntax-keyword: #859900;"))
    }

    @Test
    fun `test bubblegum barbie theme registration and switching`() = runTest {
        val themeExtension = ThemeExtension()
        val available = themeExtension.availableThemes.value

        assertTrue(available.any { it.id == "bubblegum.barbie" })

        // Apply Bubblegum Barbie
        val result = themeExtension.applyTheme("bubblegum.barbie")
        assertTrue(result.isSuccess)

        val current = themeExtension.currentTheme.value
        assertEquals("bubblegum.barbie", current.id)
        assertEquals("Bubblegum Barbie", current.name)
        assertFalse(current.isDark)
        assertEquals("#FFF5F8", current.colors.background)
        assertEquals("#4A154B", current.colors.foreground)
        assertEquals("#E0218A", current.colors.accent)
        assertEquals("#F8BBD0", current.dataGrid.headerBackground)
        assertEquals("#D81B60", current.syntax.keyword)
    }

    @Test
    fun `test css variables generation for bubblegum barbie`() {
        val css = JormungandrTheme.BUBBLEGUM_BARBIE.toCssVariables()

        assertTrue(css.contains("--jg-bg: #FFF5F8;"))
        assertTrue(css.contains("--jg-fg: #4A154B;"))
        assertTrue(css.contains("--jg-accent: #E0218A;"))
        assertTrue(css.contains("--jg-grid-header-bg: #F8BBD0;"))
        assertTrue(css.contains("--jg-syntax-keyword: #D81B60;"))
    }
}
