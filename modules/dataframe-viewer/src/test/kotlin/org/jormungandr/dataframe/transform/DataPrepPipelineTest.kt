/*
 * Copyright © 2025–2026 indoctrinatedrecluse
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

package org.jormungandr.dataframe.transform

import org.jormungandr.dataframe.model.ColumnMetadata
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class DataPrepPipelineTest {

    private fun createSampleDf(): DataFrame {
        val cols = listOf(
            ColumnMetadata("id", "integer", DataTypeCategory.INTEGER),
            ColumnMetadata("name", "string", DataTypeCategory.STRING),
            ColumnMetadata("age", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("salary", "float", DataTypeCategory.FLOAT),
            ColumnMetadata("department", "string", DataTypeCategory.STRING)
        )
        val rows = listOf(
            listOf(1L, "  Alice  ", 25.0, 50000.0, "Engineering"),
            listOf(2L, "BOB!", null, 80000.0, "Marketing"),
            listOf(3L, "charlie", 35.0, null, "Engineering"),
            listOf(4L, "Diana", 45.0, 150000.0, "Executive"),
            listOf(5L, "Evan", 29.0, 60000.0, "Marketing"),
            listOf(5L, "Evan", 29.0, 60000.0, "Marketing") // duplicate
        )
        return DataFrame("employees", cols, rows)
    }

    @Test
    fun `test deduplication step`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.Deduplicate())

        val result = pipeline.apply(df)
        assertEquals(5, result.rowCount)
    }

    @Test
    fun `test drop na step`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.DropNa(listOf("age", "salary"), DropNaHow.ANY))

        val result = pipeline.apply(df)
        // Rows with null age or null salary are dropped (rows 2 and 3)
        assertEquals(4, result.rowCount)
    }

    @Test
    fun `test fill na with mean and constant`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.FillNa("age", FillStrategy.MEAN))
        pipeline.addStep(DataPrepStep.FillNa("salary", FillStrategy.CONSTANT, "55000.0"))

        val result = pipeline.apply(df)
        val ageIdx = result.getColumnIndex("age")
        val salaryIdx = result.getColumnIndex("salary")

        // No nulls should remain in age or salary
        assertTrue(result.rows.all { it[ageIdx] != null })
        assertTrue(result.rows.all { it[salaryIdx] != null })
    }

    @Test
    fun `test string cleaning operations`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.StringClean("name", StringCleanOp.TRIM))
        pipeline.addStep(DataPrepStep.StringClean("name", StringCleanOp.LOWERCASE))
        pipeline.addStep(DataPrepStep.StringClean("name", StringCleanOp.REMOVE_PUNCTUATION))

        val result = pipeline.apply(df)
        val nameIdx = result.getColumnIndex("name")

        assertEquals("alice", result.rows[0][nameIdx])
        assertEquals("bob", result.rows[1][nameIdx])
        assertEquals("charlie", result.rows[2][nameIdx])
    }

    @Test
    fun `test numerical scaling min-max`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.FillNa("salary", FillStrategy.CONSTANT, "50000.0"))
        pipeline.addStep(DataPrepStep.NumericalScale("salary", ScaleMethod.MIN_MAX))

        val result = pipeline.apply(df)
        val salIdx = result.getColumnIndex("salary")
        val values = result.rows.mapNotNull { (it[salIdx] as? Number)?.toDouble() }

        assertTrue(values.minOrNull()!! >= 0.0)
        assertTrue(values.maxOrNull()!! <= 1.0)
    }

    @Test
    fun `test outlier clipping`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.FillNa("salary", FillStrategy.CONSTANT, "50000.0"))
        pipeline.addStep(DataPrepStep.OutlierClip("salary", ClipMethod.CUSTOM_BOUNDS, 55000.0, 100000.0))

        val result = pipeline.apply(df)
        val salIdx = result.getColumnIndex("salary")
        val values = result.rows.mapNotNull { (it[salIdx] as? Number)?.toDouble() }

        assertTrue(values.all { it in 55000.0..100000.0 })
    }

    @Test
    fun `test one hot encoding`() {
        val df = createSampleDf()
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.OneHotEncode("department"))

        val result = pipeline.apply(df)
        val colNames = result.columns.map { it.name }

        assertTrue(colNames.any { it.contains("department_Engineering") })
        assertTrue(colNames.any { it.contains("department_Marketing") })
        assertTrue(colNames.any { it.contains("department_Executive") })
    }

    @Test
    fun `test code generation pandas polars sql`() {
        val pipeline = DataPrepPipeline()
        pipeline.addStep(DataPrepStep.Deduplicate())
        pipeline.addStep(DataPrepStep.FillNa("age", FillStrategy.MEDIAN))
        pipeline.addStep(DataPrepStep.StringClean("name", StringCleanOp.TRIM))
        pipeline.addStep(DataPrepStep.NumericalScale("salary", ScaleMethod.MIN_MAX))

        val pandas = pipeline.generatePandasCode()
        assertTrue(pandas.contains("import pandas as pd"))
        assertTrue(pandas.contains("df.drop_duplicates()"))
        assertTrue(pandas.contains("df['age'].fillna(df['age'].median())"))
        assertTrue(pandas.contains("df['name'].str.strip()"))

        val polars = pipeline.generatePolarsCode()
        assertTrue(polars.contains("import polars as pl"))
        assertTrue(polars.contains(".unique()"))
        assertTrue(polars.contains("pl.col(\"age\").fill_null(pl.col(\"age\").median())"))

        val sql = pipeline.generateSqlCode("employees")
        assertTrue(sql.contains("WITH step_0 AS"))
        assertTrue(sql.contains("SELECT DISTINCT * FROM step_0"))
    }
}
