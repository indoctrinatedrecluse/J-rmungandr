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

package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import java.awt.Dimension
import javax.swing.JComponent

/**
 * Dialog wrapper for searching and recalling query execution history.
 */
class QueryHistoryDialog(
    project: Project?,
    private val onSelectQuery: (String) -> Unit
) : DialogWrapper(project, true) {

    private lateinit var historyPanel: QueryHistoryPanel
    var selectedSql: String? = null
        private set

    init {
        title = "Query Execution History"
        init()
    }

    override fun createCenterPanel(): JComponent {
        historyPanel = QueryHistoryPanel { sql ->
            selectedSql = sql
            onSelectQuery(sql)
            close(OK_EXIT_CODE)
        }
        historyPanel.preferredSize = Dimension(750, 420)
        return historyPanel
    }
}
