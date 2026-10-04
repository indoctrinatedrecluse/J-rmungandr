package org.jormungandr.core.extension

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.jormungandr.core.extension.impl.ExtensionManagerImpl
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class MockExtension(
    override val id: ExtensionId = ExtensionId("mock-plugin"),
    override val metadata: ExtensionMetadata = ExtensionMetadata(
        id = id,
        displayName = "Mock Plugin",
        version = "1.0.0",
        description = "Test plugin"
    )
) : JormungandrExtension {
    override var state: ExtensionState = ExtensionState.UNLOADED
    var wasInitialized = false
    var wasActivated = false
    var wasDeactivated = false
    var wasDisposed = false
    var trimmedLevel: MemoryPressureLevel? = null

    override suspend fun initialize(context: ExtensionContext) {
        wasInitialized = true
        state = ExtensionState.INITIALIZED
    }

    override suspend fun activate() {
        wasActivated = true
        state = ExtensionState.ACTIVE
    }

    override suspend fun pause() {
        state = ExtensionState.PAUSED
    }

    override suspend fun resume() {
        state = ExtensionState.ACTIVE
    }

    override suspend fun trimMemory(level: MemoryPressureLevel) {
        trimmedLevel = level
    }

    override suspend fun deactivate() {
        wasDeactivated = true
        state = ExtensionState.DISPOSING
    }

    override fun dispose() {
        wasDisposed = true
        state = ExtensionState.TERMINATED
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionManagerTest {

    @Test
    fun `test extension registration and state lookup`() = runTest {
        val manager = ExtensionManagerImpl(this)
        val ext = MockExtension()

        manager.registerExtension(ext)
        assertNotNull(manager.getExtension(ext.id))
        assertEquals(ExtensionState.UNLOADED, manager.getExtensionState(ext.id))
    }

    @Test
    fun `test extension activation and memory trimming`() = runTest {
        val manager = ExtensionManagerImpl(this)
        val ext = MockExtension()
        manager.registerExtension(ext)

        manager.activateExtension(ext.id)
        assertTrue(ext.wasActivated)
        assertEquals(ExtensionState.ACTIVE, manager.getExtensionState(ext.id))

        manager.trimAllMemory(MemoryPressureLevel.CRITICAL)
        assertEquals(MemoryPressureLevel.CRITICAL, ext.trimmedLevel)

        manager.deactivateExtension(ext.id)
        assertTrue(ext.wasDeactivated)
    }
}
