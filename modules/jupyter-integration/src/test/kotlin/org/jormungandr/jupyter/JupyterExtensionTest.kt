package org.jormungandr.jupyter

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import org.jormungandr.core.extension.ExtensionContext
import org.jormungandr.core.extension.ExtensionState
import org.jormungandr.core.extension.MemoryPressureLevel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class JupyterExtensionTest {

    @Test
    fun testExtensionMetadata() {
        val extension = JupyterExtension()
        assertEquals("org.jormungandr.jupyter", extension.id.value)
        assertEquals("Jupyter Interactive Subsystem", extension.metadata.displayName)
        assertTrue(extension.metadata.supportedLanguages.contains("Python"))
        assertTrue(extension.metadata.associatedStacks.contains("ZeroMQ"))
        assertEquals(ExtensionState.UNLOADED, extension.state)
    }

    @Test
    fun testLifecycleTransitions() = runBlocking {
        val extension = JupyterExtension()
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
        assertNotNull(extension.discoveredKernels)

        extension.pause()
        assertEquals(ExtensionState.PAUSED, extension.state)

        extension.resume()
        assertEquals(ExtensionState.ACTIVE, extension.state)

        extension.trimMemory(MemoryPressureLevel.MODERATE)
        assertEquals(ExtensionState.ACTIVE, extension.state)

        extension.deactivate()
        assertEquals(ExtensionState.TERMINATED, extension.state)
        assertTrue(extension.discoveredKernels.isEmpty())
    }
}
