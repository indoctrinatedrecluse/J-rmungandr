package org.jormungandr.jupyter.kernel

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KernelDiscoveryTest {

    @Test
    fun `test discoverKernels always provides at least one Python kernel spec`() {
        val kernels = KernelDiscovery.discoverKernels()
        assertTrue(kernels.isNotEmpty(), "At least one kernel specification should be discovered or provided by default")

        val pythonKernel = kernels.firstOrNull { it.language.equals("python", ignoreCase = true) }
        assertNotNull(pythonKernel, "A Python kernel spec must be available")
        assertTrue(pythonKernel?.argv?.isNotEmpty() == true)
    }

    @Test
    fun `test findPythonExecutable finds a valid candidate`() {
        val pythonExe = KernelDiscovery.findPythonExecutable()
        assertTrue(pythonExe.isNotBlank())
        assertTrue(pythonExe.contains("python", ignoreCase = true) || pythonExe.contains("py", ignoreCase = true))
    }
}
