package org.jormungandr.dataframe

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jormungandr.core.extension.ExtensionContext
import org.jormungandr.core.extension.ExtensionState
import org.jormungandr.core.extension.MemoryPressureLevel
import org.jormungandr.core.theme.JormungandrTheme
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataFrameViewerExtensionTest {

    @Test
    fun `test extension metadata and lifecycle`() = runBlocking {
        val extension = DataFrameViewerExtension()
        assertEquals("org.jormungandr.dataframe", extension.id.value)
        assertEquals("Arrow Dataframe Viewer Subsystem", extension.metadata.displayName)
        assertEquals(ExtensionState.UNLOADED, extension.state)

        val dummyContext = object : ExtensionContext {
            override val project = null
            override val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            override val extensionId = extension.id
            override fun logInfo(message: String) {}
            override fun logWarn(message: String) {}
            override fun logError(message: String, throwable: Throwable?) {}
        }

        extension.initialize(dummyContext)
        assertEquals(ExtensionState.INITIALIZED, extension.state)

        extension.activate()
        assertEquals(ExtensionState.ACTIVE, extension.state)

        extension.applyTheme(JormungandrTheme.BUBBLEGUM_BARBIE)
        assertEquals(JormungandrTheme.BUBBLEGUM_BARBIE.dataGrid, extension.currentGridTheme)

        extension.pause()
        assertEquals(ExtensionState.PAUSED, extension.state)

        extension.resume()
        assertEquals(ExtensionState.ACTIVE, extension.state)

        extension.trimMemory(MemoryPressureLevel.MODERATE)
        assertEquals(ExtensionState.ACTIVE, extension.state)

        extension.deactivate()
        assertEquals(ExtensionState.TERMINATED, extension.state)
    }
}
