package org.jormungandr.database

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jormungandr.core.extension.ExtensionContext
import org.jormungandr.core.extension.ExtensionState
import org.jormungandr.core.extension.MemoryPressureLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DatabaseSuiteExtensionTest {

    @Test
    fun `test database extension lifecycle and metadata`() = runBlocking {
        val extension = DatabaseSuiteExtension()
        assertEquals("org.jormungandr.database", extension.id.value)
        assertEquals("Database & Query Studio", extension.metadata.displayName)
        assertTrue(extension.metadata.supportedLanguages.contains("SQL"))
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
