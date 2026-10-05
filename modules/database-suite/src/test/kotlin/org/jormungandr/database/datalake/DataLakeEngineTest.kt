/*
 * Copyright (c) 2025-2026 indoctrinatedrecluse
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

package org.jormungandr.database.datalake

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

class DataLakeEngineTest {

    private val sampleS3Config = ConnectionConfig(
        id = UUID.randomUUID().toString(),
        name = "Amazon S3 Analytics",
        dialect = DatabaseDialect.S3_DATA_LAKE,
        databaseName = "analytics-data-lake"
    )

    @Test
    fun `test listObjects returns partition directories when prefix is empty`() {
        val rootItems = DataLakeEngine.listObjects(sampleS3Config, "")
        assertTrue(rootItems.isNotEmpty())
        assertTrue(rootItems.all { it.isPrefix })
        assertTrue(rootItems.any { it.name == "nyc_taxi" })
        assertTrue(rootItems.any { it.name == "weather_stations" })
        assertTrue(rootItems.any { it.name == "ml_feature_store" })
    }

    @Test
    fun `test listObjects returns parquet files when prefix specified`() {
        val taxiItems = DataLakeEngine.listObjects(sampleS3Config, "nyc_taxi")
        assertTrue(taxiItems.isNotEmpty())
        assertTrue(taxiItems.all { !it.isPrefix })
        assertTrue(taxiItems.all { it.format == StorageFormat.PARQUET })
        assertTrue(taxiItems.any { it.name.contains("yellow_tripdata") })

        val first = taxiItems.first()
        assertTrue(first.uri.startsWith("s3://analytics-data-lake/nyc_taxi/"))
        assertTrue(first.sizeBytes > 0)
        assertFalse(first.sizeFormatted.isBlank())
    }

    @Test
    fun `test previewRemoteDataset parquet to dataframe conversion`() {
        val item = RemoteObjectItem(
            key = "nyc_taxi/year=2026/yellow_tripdata_2026_01.parquet",
            sizeBytes = 48_000_000L,
            lastModified = "2026-02-01",
            format = StorageFormat.PARQUET,
            isPrefix = false,
            uri = "s3://analytics-data-lake/nyc_taxi/year=2026/yellow_tripdata_2026_01.parquet"
        )

        val df = DataLakeEngine.previewRemoteDataset(sampleS3Config, item)
        assertNotNull(df)
        assertTrue(df.rowCount > 0)
        assertTrue(df.columnCount >= 7)

        val fareCol = df.columns.find { it.name == "fare_amount" }
        assertNotNull(fareCol)
        assertTrue(fareCol!!.isNumeric)
    }

    @Test
    fun `test generateDuckDbQuery produces zero-copy SQL`() {
        val item = RemoteObjectItem(
            key = "weather_stations/observations_2026.parquet",
            sizeBytes = 18_000_000L,
            lastModified = "2026-10-04",
            format = StorageFormat.PARQUET,
            isPrefix = false,
            uri = "s3://analytics-data-lake/weather_stations/observations_2026.parquet"
        )

        val sql = DataLakeEngine.generateDuckDbQuery(item, limit = 50)
        assertTrue(sql.contains("INSTALL httpfs;"))
        assertTrue(sql.contains("LOAD httpfs;"))
        assertTrue(sql.contains("read_parquet('s3://analytics-data-lake/weather_stations/observations_2026.parquet')"))
        assertTrue(sql.contains("LIMIT 50;"))
    }

    @Test
    fun `test generatePythonReaderCode generates Polars and DuckDB scripts`() {
        val item = RemoteObjectItem(
            key = "ml_feature_store/customer_churn_features_v2.parquet",
            sizeBytes = 29_000_000L,
            lastModified = "2026-10-02",
            format = StorageFormat.PARQUET,
            isPrefix = false,
            uri = "s3://analytics-data-lake/ml_feature_store/customer_churn_features_v2.parquet"
        )

        val py = DataLakeEngine.generatePythonReaderCode(item)
        assertTrue(py.contains("import polars as pl"))
        assertTrue(py.contains("scan_parquet"))
        assertTrue(py.contains("import duckdb"))
    }
}
