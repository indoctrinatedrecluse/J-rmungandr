package org.jormungandr.jupyter.kernel

import kotlinx.coroutines.test.runTest
import org.jormungandr.jupyter.model.CellOutput
import org.jormungandr.jupyter.model.JupyterKernelSpec
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.util.concurrent.TimeUnit

class SubprocessPythonSessionTest {

    private var session: SubprocessPythonSession? = null

    @BeforeEach
    fun setUp() {
        val spec = JupyterKernelSpec("python3", "Python 3", "python", listOf("python"))
        session = SubprocessPythonSession(spec)
    }

    @AfterEach
    fun tearDown() = runTest {
        session?.shutdown()
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test live cell execution with stream output`() = runTest {
        val s = session ?: return@runTest
        val started = s.start()
        if (!started) {
            // If Python is not installed on runner, pass gracefully
            return@runTest
        }

        val captured = mutableListOf<CellOutput>()
        val result = s.execute("val = 21 * 2\nprint(f'Computed answer: {val}')") { out ->
            captured.add(out)
        }

        assertTrue(result.isSuccess)
        assertEquals(1, result.executionCount)
        assertTrue(captured.any { it is CellOutput.StreamOutput && it.text.contains("Computed answer: 42") })
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test state persistence across sequential cells`() = runTest {
        val s = session ?: return@runTest
        val started = s.start()
        if (!started) return@runTest

        // Cell 1: Define variable
        val r1 = s.execute("x = 100")
        assertTrue(r1.isSuccess)

        // Cell 2: Access variable defined in cell 1
        val captured = mutableListOf<CellOutput>()
        val r2 = s.execute("print(x + 23)") { out ->
            captured.add(out)
        }

        assertTrue(r2.isSuccess)
        assertEquals(2, r2.executionCount)
        assertTrue(captured.any { it is CellOutput.StreamOutput && it.text.contains("123") })
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test exception handling in cell execution`() = runTest {
        val s = session ?: return@runTest
        val started = s.start()
        if (!started) return@runTest

        val captured = mutableListOf<CellOutput>()
        val result = s.execute("1 / 0") { out ->
            captured.add(out)
        }

        assertFalse(result.isSuccess)
        val err = captured.firstOrNull { it is CellOutput.ErrorOutput } as? CellOutput.ErrorOutput
        assertNotNull(err)
        assertTrue(err?.ename?.contains("ZeroDivisionError") == true || err?.traceback?.any { it.contains("ZeroDivisionError") } == true)
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    fun `test expression evaluation as cell output`() = runTest {
        val s = session ?: return@runTest
        val started = s.start()
        if (!started) return@runTest

        val captured = mutableListOf<CellOutput>()
        val result = s.execute("a = 15\nb = 27\na + b") { out ->
            captured.add(out)
        }

        assertTrue(result.isSuccess)
        assertTrue(captured.any { it is CellOutput.StreamOutput && it.text.contains("42") })
    }
}
