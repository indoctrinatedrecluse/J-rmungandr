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

import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Represents an entry in the query execution history log.
 */
data class QueryHistoryEntry(
    val id: String = UUID.randomUUID().toString(),
    val connectionId: String,
    val connectionName: String,
    val query: String,
    val timestamp: Long = System.currentTimeMillis(),
    val durationMs: Long,
    val rowCount: Int,
    val isSuccess: Boolean,
    val errorMessage: String? = null
)

/**
 * Manages SQL query execution history, providing in-memory retention, searching, and filtering.
 */
object QueryHistoryManager {

    private const val MAX_HISTORY_SIZE = 1000
    private val history = CopyOnWriteArrayList<QueryHistoryEntry>()

    /**
     * Records a new query execution entry.
     */
    fun record(entry: QueryHistoryEntry) {
        history.add(0, entry) // Prepend newest first
        while (history.size > MAX_HISTORY_SIZE) {
            history.removeAt(history.lastIndex)
        }
    }

    /**
     * Convenience method to record an execution.
     */
    fun recordExecution(
        connectionId: String,
        connectionName: String,
        query: String,
        durationMs: Long,
        rowCount: Int,
        isSuccess: Boolean,
        errorMessage: String? = null
    ): QueryHistoryEntry {
        val entry = QueryHistoryEntry(
            connectionId = connectionId,
            connectionName = connectionName,
            query = query,
            durationMs = durationMs,
            rowCount = rowCount,
            isSuccess = isSuccess,
            errorMessage = errorMessage
        )
        record(entry)
        return entry
    }

    /**
     * Returns history filtered by connection ID and optional text search query.
     */
    fun getHistory(
        connectionId: String? = null,
        searchQuery: String? = null,
        limit: Int = 100
    ): List<QueryHistoryEntry> {
        return history.asSequence()
            .filter { entry ->
                connectionId == null || entry.connectionId == connectionId
            }
            .filter { entry ->
                searchQuery.isNullOrBlank() || entry.query.contains(searchQuery, ignoreCase = true)
            }
            .take(limit)
            .toList()
    }

    /**
     * Returns all history items.
     */
    fun getAll(): List<QueryHistoryEntry> = history.toList()

    /**
     * Clears query execution history.
     */
    fun clear(connectionId: String? = null) {
        if (connectionId == null) {
            history.clear()
        } else {
            history.removeIf { it.connectionId == connectionId }
        }
    }

    /**
     * Returns the count of recorded query executions.
     */
    fun size(): Int = history.size
}
