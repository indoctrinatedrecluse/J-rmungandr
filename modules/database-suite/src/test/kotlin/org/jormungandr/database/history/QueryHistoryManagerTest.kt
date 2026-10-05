/*
 * Copyright 2025–2026 indoctrinatedrecluse
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jormungandr.database.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class QueryHistoryManagerTest {

    @BeforeEach
    fun setUp() {
        QueryHistoryManager.clear()
    }

    @Test
    fun `records queries and retrieves by filter`() {
        QueryHistoryManager.recordExecution(
            connectionId = "c1",
            connectionName = "Postgres Main",
            query = "SELECT * FROM users;",
            durationMs = 25,
            rowCount = 10,
            isSuccess = true
        )

        QueryHistoryManager.recordExecution(
            connectionId = "c2",
            connectionName = "SQLite Analytics",
            query = "SELECT COUNT(*) FROM logs WHERE level = 'ERROR';",
            durationMs = 5,
            rowCount = 1,
            isSuccess = true
        )

        assertEquals(2, QueryHistoryManager.size())

        // Filter by connection
        val c1History = QueryHistoryManager.getHistory(connectionId = "c1")
        assertEquals(1, c1History.size)
        assertEquals("SELECT * FROM users;", c1History[0].query)

        // Filter by search query
        val searchLogs = QueryHistoryManager.getHistory(searchQuery = "logs")
        assertEquals(1, searchLogs.size)
        assertEquals("c2", searchLogs[0].connectionId)

        // Clear for c1 only
        QueryHistoryManager.clear(connectionId = "c1")
        assertEquals(1, QueryHistoryManager.size())
        assertEquals("c2", QueryHistoryManager.getAll()[0].connectionId)

        // Clear all
        QueryHistoryManager.clear()
        assertEquals(0, QueryHistoryManager.size())
    }

    @Test
    fun `preserves insertion order with newest first`() {
        QueryHistoryManager.recordExecution("c1", "Db", "SELECT 1;", 1, 1, true)
        QueryHistoryManager.recordExecution("c1", "Db", "SELECT 2;", 2, 1, true)
        QueryHistoryManager.recordExecution("c1", "Db", "SELECT 3;", 3, 1, true)

        val history = QueryHistoryManager.getAll()
        assertEquals(3, history.size)
        assertEquals("SELECT 3;", history[0].query)
        assertEquals("SELECT 2;", history[1].query)
        assertEquals("SELECT 1;", history[2].query)
    }
}
