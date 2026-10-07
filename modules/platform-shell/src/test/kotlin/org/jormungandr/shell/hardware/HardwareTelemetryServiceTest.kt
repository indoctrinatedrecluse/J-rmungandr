package org.jormungandr.shell.hardware

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HardwareTelemetryServiceTest {

    @Test
    fun `test hardware telemetry snapshot returns valid devices and host memory`() {
        val snapshot = HardwareTelemetryService.fetchTelemetry()

        assertNotNull(snapshot)
        assertTrue(snapshot.devices.isNotEmpty(), "Snapshot must contain at least one accelerator or host compute device")
        assertTrue(snapshot.hostTotalRamMb > 0, "Host total RAM must be greater than zero")
        assertTrue(snapshot.hostAvailableProcessors > 0, "Available processors must be positive")

        val primary = snapshot.devices.first()
        assertNotNull(primary.name)
        assertTrue(primary.totalMemoryMb > 0, "Device memory must be greater than zero")
        assertTrue(primary.memoryUsagePercent in 0..100, "Memory usage percentage must be between 0 and 100")
        assertTrue(primary.utilizationPercent in 0..100, "Compute utilization must be between 0 and 100")
    }

    @Test
    fun `test flush vram cache returns informative message`() {
        val message = HardwareTelemetryService.flushVramCache()
        assertNotNull(message)
        assertTrue(message.contains("purged and garbage collected"), "Should confirm GC and cache purging")
    }
}
