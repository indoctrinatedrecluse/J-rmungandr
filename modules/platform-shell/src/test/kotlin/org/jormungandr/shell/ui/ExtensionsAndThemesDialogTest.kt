package org.jormungandr.shell.ui

import org.jormungandr.core.extension.ExtensionId
import org.jormungandr.core.extension.impl.ExtensionManagerImpl
import org.jormungandr.core.theme.JormungandrTheme
import org.jormungandr.dataframe.DataFrameViewerExtension
import org.jormungandr.database.DatabaseSuiteExtension
import org.jormungandr.jupyter.JupyterExtension
import org.jormungandr.shell.extension.CoreExtensionsRegistry
import org.jormungandr.shell.theme.ThemeExtension
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExtensionsAndThemesDialogTest {

    @Test
    fun `test core extensions have defined supported languages and associated stacks`() {
        val dfExt = DataFrameViewerExtension()
        assertTrue(dfExt.metadata.supportedLanguages.contains("Python"))
        assertTrue(dfExt.metadata.associatedStacks.contains("Apache Arrow"))

        val dbExt = DatabaseSuiteExtension()
        assertTrue(dbExt.metadata.supportedLanguages.contains("SQL"))
        assertTrue(dbExt.metadata.associatedStacks.contains("DuckDB"))

        val jupyterExt = JupyterExtension()
        assertTrue(jupyterExt.metadata.supportedLanguages.contains("Python"))
        assertTrue(jupyterExt.metadata.associatedStacks.contains("Jupyter"))

        val themeExt = ThemeExtension()
        assertTrue(themeExt.metadata.supportedLanguages.contains("CSS"))
        assertTrue(themeExt.metadata.associatedStacks.contains("Solarized Light"))
        assertTrue(themeExt.metadata.associatedStacks.contains("Bubblegum Barbie"))
    }

    @Test
    fun `test core extensions bootstrap populates extension manager`() {
        val extensionManager = ExtensionManagerImpl()
        CoreExtensionsRegistry.ensureCoreExtensionsRegistered(extensionManager)

        val loaded = extensionManager.loadedExtensions.value
        assertTrue(loaded.containsKey(ExtensionId("org.jormungandr.dataframe")))
        assertTrue(loaded.containsKey(ExtensionId("org.jormungandr.database")))
        assertTrue(loaded.containsKey(ExtensionId("org.jormungandr.jupyter")))
    }

    @Test
    fun `test theme manager exposes solarized light and bubblegum barbie themes`() {
        val themeExtension = ThemeExtension()
        val themes = themeExtension.availableThemes.value

        assertEquals(2, themes.size)
        assertTrue(themes.any { it.id == JormungandrTheme.SOLARIZED_LIGHT.id })
        assertTrue(themes.any { it.id == JormungandrTheme.BUBBLEGUM_BARBIE.id })

        val barbie = themes.first { it.id == JormungandrTheme.BUBBLEGUM_BARBIE.id }
        assertEquals("Bubblegum Barbie", barbie.name)
        assertEquals("#FFF5F8", barbie.colors.background)
        assertEquals("#E0218A", barbie.colors.accent)
    }
}
