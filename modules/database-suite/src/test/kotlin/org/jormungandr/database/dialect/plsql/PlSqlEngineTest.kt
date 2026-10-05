package org.jormungandr.database.dialect.plsql

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class PlSqlEngineTest {

    @Test
    fun `test procedural block detection`() {
        assertTrue(PlSqlEngine.isProceduralBlock("DECLARE v_count NUMBER; BEGIN NULL; END;"))
        assertTrue(PlSqlEngine.isProceduralBlock("BEGIN DBMS_OUTPUT.PUT_LINE('Hello'); END;"))
        assertTrue(PlSqlEngine.isProceduralBlock("CREATE OR REPLACE PROCEDURE my_proc AS BEGIN NULL; END;"))
        assertTrue(PlSqlEngine.isProceduralBlock("CREATE OR REPLACE FUNCTION my_func RETURN NUMBER AS BEGIN RETURN 1; END;"))
        assertTrue(PlSqlEngine.isProceduralBlock("CREATE OR REPLACE PACKAGE my_pkg AS END;"))
        assertTrue(PlSqlEngine.isProceduralBlock("CREATE OR REPLACE TRIGGER my_trg BEFORE INSERT ON users BEGIN NULL; END;"))
        assertFalse(PlSqlEngine.isProceduralBlock("SELECT * FROM employees;"))
    }

    @Test
    fun `test anonymous block execution and dbms output capture`() {
        val script = """
            BEGIN
                DBMS_OUTPUT.PUT_LINE('Audit status: completed');
                DBMS_OUTPUT.PUT_LINE('Records processed: 100');
            END;
        """.trimIndent()

        val result = PlSqlEngine.execute(null, script)
        assertTrue(result.isSuccess)
        assertEquals(2, result.serverOutput.size)
        assertEquals("Audit status: completed", result.serverOutput[0])
        assertEquals("Records processed: 100", result.serverOutput[1])
        assertNotNull(result.dataFrame)
        assertEquals(2, result.dataFrame?.rowCount)
    }

    @Test
    fun `test procedure creation feedback`() {
        val script = PlSqlEngine.TEMPLATE_STORED_PROCEDURE
        val result = PlSqlEngine.execute(null, script)
        assertTrue(result.isSuccess)
        assertTrue(result.serverOutput.isNotEmpty())
    }
}
