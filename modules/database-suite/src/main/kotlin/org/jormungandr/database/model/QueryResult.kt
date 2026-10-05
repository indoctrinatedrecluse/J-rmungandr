package org.jormungandr.database.model

import org.jormungandr.dataframe.model.DataFrame

data class QueryResult(
    val query: String,
    val isSuccess: Boolean,
    val dataFrame: DataFrame? = null,
    val rowsAffected: Int = 0,
    val executionTimeMs: Long = 0L,
    val errorMessage: String? = null
)
