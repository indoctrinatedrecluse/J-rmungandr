//
// Copyright 2025–2026 indoctrinatedrecluse
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

package org.jormungandr.dataframe.ui

import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class DataScienceCopilotPanelTest {

    @Test
    fun `test DataScienceCopilotPanel initializes without NullPointerException`() {
        val columns = listOf(
            Pair("metric", DataTypeCategory.STRING),
            Pair("value", DataTypeCategory.FLOAT)
        )
        val rows: List<List<Any?>> = listOf(
            listOf("latency", 12.5),
            listOf("throughput", 450.0)
        )
        val df = DataFrame.buildWithStatistics("test_df", columns, rows)

        val panel = DataScienceCopilotPanel(df)
        assertNotNull(panel, "Copilot panel must be non-null after construction")
    }

    @Test
    fun `test DataFrameGridPanel initializes with Copilot without exceptions`() {
        val columns = listOf(
            Pair("id", DataTypeCategory.INTEGER),
            Pair("name", DataTypeCategory.STRING)
        )
        val rows: List<List<Any?>> = listOf(
            listOf(1L, "Alice"),
            listOf(2L, "Bob")
        )
        val df = DataFrame.buildWithStatistics("users", columns, rows)

        val gridPanel = DataFrameGridPanel(df)
        assertNotNull(gridPanel, "Grid panel must be non-null after construction")
    }
}
