package org.jormungandr.database.nosql.mongo

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class MongoEngineTest {

    @Test
    fun `test list databases and collections`() {
        val dbs = MongoEngine.listDatabases()
        assertTrue(dbs.contains("ecom_store"))

        val colls = MongoEngine.listCollections(database = "ecom_store")
        assertTrue(colls.contains("customers"))
        assertTrue(colls.contains("orders"))
        assertTrue(colls.contains("products"))
    }

    @Test
    fun `test find with filter and projection`() {
        val docs = MongoEngine.find(
            database = "ecom_store",
            collection = "customers",
            filterJson = """{"tier": "gold"}""",
            projectionJson = """{"name": 1, "tier": 1}"""
        )
        assertFalse(docs.isEmpty())
        assertTrue(docs.all { it.get("tier")?.asString == "gold" })
    }

    @Test
    fun `test aggregate pipeline`() {
        val docs = MongoEngine.aggregate(
            database = "ecom_store",
            collection = "customers",
            pipelineJson = """[{"${'$'}match": {"active": true}}, {"${'$'}limit": 2}]"""
        )
        assertEquals(2, docs.size)
    }

    @Test
    fun `test document count`() {
        val total = MongoEngine.countDocuments(database = "ecom_store", collection = "customers")
        assertTrue(total >= 5)
    }

    @Test
    fun `test convert to data frame`() {
        val docs = MongoEngine.find(database = "ecom_store", collection = "customers", limit = 10)
        val df = MongoEngine.toDataFrame(docs, "test_customers")
        assertEquals(docs.size, df.rowCount)
        assertTrue(df.columns.any { it.name == "name" })
        assertTrue(df.columns.any { it.name == "email" })
    }

    @Test
    fun `test insert and delete document`() {
        val docId = MongoEngine.insertOne(
            database = "ecom_store",
            collection = "customers",
            docJson = """{"name": "Test User", "tier": "bronze", "active": false}"""
        )
        assertNotNull(docId)

        val found = MongoEngine.find(
            database = "ecom_store",
            collection = "customers",
            filterJson = """{"name": "Test User"}"""
        )
        assertEquals(1, found.size)

        val deletedCount = MongoEngine.deleteDocuments(
            database = "ecom_store",
            collection = "customers",
            filterJson = """{"name": "Test User"}"""
        )
        assertEquals(1, deletedCount)
    }
}
