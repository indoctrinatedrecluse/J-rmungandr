package org.jormungandr.database.nosql.kafka

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class KafkaEngineTest {

    @Test
    fun `test list topics and partitions`() {
        val topics = KafkaEngine.listTopics()
        assertTrue(topics.any { it.name == "user-events" })
        assertTrue(topics.any { it.name == "payment-transactions" })
        assertTrue(topics.any { it.name == "iot-sensor-stream" })

        val ueTopic = topics.find { it.name == "user-events" }
        assertNotNull(ueTopic)
        assertEquals(3, ueTopic!!.partitionCount)
        assertTrue(ueTopic.messageCount > 0)
    }

    @Test
    fun `test produce and consume events`() {
        val record = KafkaEngine.produce(
            topic = "user-events",
            partition = 0,
            key = "user_999",
            value = """{"event": "unit_test_event"}"""
        )
        assertNotNull(record)
        assertEquals("user-events", record.topic)
        assertEquals("user_999", record.key)

        val consumed = KafkaEngine.consume(
            topic = "user-events",
            partition = 0,
            maxRecords = 10,
            fromBeginning = false
        )
        assertTrue(consumed.isNotEmpty())
        assertEquals("user_999", consumed.last().key)
    }

    @Test
    fun `test consumer groups and lag monitoring`() {
        val groups = KafkaEngine.listConsumerGroups()
        assertTrue(groups.isNotEmpty())
        assertTrue(groups.any { it.groupId == "analytics-pipeline" })
        assertTrue(groups.any { it.groupId == "fraud-detector" })
    }

    @Test
    fun `test records to dataframe`() {
        val records = KafkaEngine.consume(topic = "user-events", maxRecords = 10, fromBeginning = true)
        val df = KafkaEngine.recordsToDataFrame(records, "test_events")
        assertEquals(records.size, df.rowCount)
        assertEquals(5, df.columns.size)
        assertTrue(df.columns.any { it.name == "partition" })
        assertTrue(df.columns.any { it.name == "offset" })
        assertTrue(df.columns.any { it.name == "value_payload" })
    }
}
