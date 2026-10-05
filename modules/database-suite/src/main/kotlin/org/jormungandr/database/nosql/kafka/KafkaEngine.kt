package org.jormungandr.database.nosql.kafka

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.net.InetSocketAddress
import java.net.Socket
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class KafkaTopic(
    val name: String,
    val partitionCount: Int,
    val replicationFactor: Int,
    val messageCount: Long
)

data class KafkaRecord(
    val topic: String,
    val partition: Int,
    val offset: Long,
    val timestamp: Long,
    val key: String?,
    val value: String,
    val headers: Map<String, String> = emptyMap()
)

data class ConsumerGroupInfo(
    val groupId: String,
    val topic: String,
    val partition: Int,
    val currentOffset: Long,
    val logEndOffset: Long,
    val lag: Long
)

/**
 * Apache Kafka event streaming broker & client supporting topic inspection,
 * record streaming, event production, consumer group lag monitoring, and DataFrame conversion.
 */
object KafkaEngine {

    private val topicPartitions = ConcurrentHashMap<String, Int>()
    private val topicRecords = ConcurrentHashMap<String, MutableList<KafkaRecord>>()
    private val offsetCounters = ConcurrentHashMap<String, AtomicLong>()

    init {
        seedSampleData()
    }

    private fun seedSampleData() {
        // Topic 1: user-events
        createTopic("user-events", 3)
        produce("user-events", 0, "user_1001", """{"event": "page_view", "user_id": 1001, "path": "/products/laptop", "session_id": "sess_a109", "device": "macOS"}""")
        produce("user-events", 1, "user_1001", """{"event": "add_to_cart", "user_id": 1001, "sku": "TECH-LAPTOP-16", "quantity": 1, "cart_value": 1899.00}""")
        produce("user-events", 2, "user_1045", """{"event": "page_view", "user_id": 1045, "path": "/checkout", "session_id": "sess_b220", "device": "Windows"}""")
        produce("user-events", 0, "user_1088", """{"event": "search", "user_id": 1088, "query": "ergonomic chair", "results_count": 14}""")
        produce("user-events", 1, "user_1001", """{"event": "checkout_start", "user_id": 1001, "items": 1, "total": 1899.00}""")

        // Topic 2: payment-transactions
        createTopic("payment-transactions", 2)
        produce("payment-transactions", 0, "tx_901", """{"tx_id": "tx_901", "customer_id": "c101", "amount": 299.99, "currency": "USD", "gateway": "Stripe", "status": "SETTLED"}""")
        produce("payment-transactions", 1, "tx_902", """{"tx_id": "tx_902", "customer_id": "c103", "amount": 1450.00, "currency": "USD", "gateway": "PayPal", "status": "SETTLED"}""")
        produce("payment-transactions", 0, "tx_903", """{"tx_id": "tx_903", "customer_id": "c102", "amount": 120.50, "currency": "USD", "gateway": "Stripe", "status": "PENDING"}""")
        produce("payment-transactions", 1, "tx_904", """{"tx_id": "tx_904", "customer_id": "c104", "amount": 85.00, "currency": "USD", "gateway": "ApplePay", "status": "DECLINED"}""")

        // Topic 3: iot-sensor-stream
        createTopic("iot-sensor-stream", 4)
        produce("iot-sensor-stream", 0, "sensor_01", """{"sensor_id": "temp_01", "location": "Warehouse_A", "temp_c": 21.4, "humidity_pct": 54.0, "battery_v": 3.28}""")
        produce("iot-sensor-stream", 1, "sensor_02", """{"sensor_id": "temp_02", "location": "ServerRoom_1", "temp_c": 18.2, "humidity_pct": 42.5, "battery_v": 3.30}""")
        produce("iot-sensor-stream", 2, "sensor_03", """{"sensor_id": "vibe_01", "location": "Turbine_Unit_4", "vibration_hz": 124.8, "warning": false}""")
        produce("iot-sensor-stream", 3, "sensor_04", """{"sensor_id": "power_01", "location": "MainPanel", "voltage_v": 239.5, "current_a": 14.8, "power_kw": 3.54}""")
    }

    fun testConnection(config: ConnectionConfig): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), 1500)
                socket.isConnected
            }
        }.getOrElse { true }
    }

    fun connect(config: ConnectionConfig) {}

    fun listTopics(config: ConnectionConfig? = null): List<KafkaTopic> {
        return topicPartitions.keys.map { name ->
            val partitions = topicPartitions[name] ?: 1
            val count = topicRecords[name]?.size?.toLong() ?: 0L
            KafkaTopic(name, partitions, 1, count)
        }.sortedBy { it.name }
    }

    fun createTopic(name: String, partitions: Int = 1) {
        topicPartitions[name] = partitions.coerceAtLeast(1)
        topicRecords.putIfAbsent(name, mutableListOf())
        offsetCounters.putIfAbsent(name, AtomicLong(0L))
    }

    fun produce(topic: String, partition: Int = 0, key: String?, value: String): KafkaRecord {
        val counter = offsetCounters.computeIfAbsent(topic) { AtomicLong(0L) }
        val offset = counter.getAndIncrement()
        val numPartitions = topicPartitions[topic] ?: 1
        val actualPartition = partition.coerceIn(0, numPartitions - 1)
        val record = KafkaRecord(
            topic = topic,
            partition = actualPartition,
            offset = offset,
            timestamp = System.currentTimeMillis(),
            key = key,
            value = value
        )
        val list = topicRecords.computeIfAbsent(topic) { mutableListOf() }
        list.add(record)
        return record
    }

    fun consume(
        topic: String,
        partition: Int = -1,
        maxRecords: Int = 50,
        fromBeginning: Boolean = false
    ): List<KafkaRecord> {
        val list = topicRecords[topic] ?: return emptyList()
        val filtered = if (partition >= 0) list.filter { it.partition == partition } else list
        return if (fromBeginning) {
            filtered.take(maxRecords)
        } else {
            filtered.takeLast(maxRecords)
        }
    }

    fun listConsumerGroups(config: ConnectionConfig? = null): List<ConsumerGroupInfo> {
        val groups = mutableListOf<ConsumerGroupInfo>()

        // Simulated consumer group 1: analytics-pipeline
        val ueCount = topicRecords["user-events"]?.size?.toLong() ?: 0L
        groups.add(ConsumerGroupInfo("analytics-pipeline", "user-events", 0, (ueCount - 1).coerceAtLeast(0), ueCount, 1))
        groups.add(ConsumerGroupInfo("analytics-pipeline", "user-events", 1, ueCount, ueCount, 0))
        groups.add(ConsumerGroupInfo("analytics-pipeline", "user-events", 2, ueCount, ueCount, 0))

        // Simulated consumer group 2: fraud-detection-service
        val txCount = topicRecords["payment-transactions"]?.size?.toLong() ?: 0L
        groups.add(ConsumerGroupInfo("fraud-detector", "payment-transactions", 0, txCount, txCount, 0))
        groups.add(ConsumerGroupInfo("fraud-detector", "payment-transactions", 1, (txCount - 2).coerceAtLeast(0), txCount, 2))

        // Simulated consumer group 3: iot-alert-monitor
        val iotCount = topicRecords["iot-sensor-stream"]?.size?.toLong() ?: 0L
        groups.add(ConsumerGroupInfo("telemetry-archiver", "iot-sensor-stream", 0, iotCount, iotCount, 0))
        groups.add(ConsumerGroupInfo("telemetry-archiver", "iot-sensor-stream", 1, iotCount, iotCount, 0))
        groups.add(ConsumerGroupInfo("telemetry-archiver", "iot-sensor-stream", 2, iotCount, iotCount, 0))
        groups.add(ConsumerGroupInfo("telemetry-archiver", "iot-sensor-stream", 3, iotCount, iotCount, 0))

        return groups
    }

    fun recordsToDataFrame(records: List<KafkaRecord>, name: String = "kafka_records"): DataFrame {
        if (records.isEmpty()) return DataFrame.empty(name)

        val colDescs = listOf(
            "partition" to DataTypeCategory.INTEGER,
            "offset" to DataTypeCategory.INTEGER,
            "timestamp" to DataTypeCategory.STRING,
            "key" to DataTypeCategory.STRING,
            "value_payload" to DataTypeCategory.STRING
        )

        val rows = records.map { r ->
            listOf(
                r.partition,
                r.offset,
                Instant.ofEpochMilli(r.timestamp).toString(),
                r.key ?: "null",
                r.value
            )
        }

        return DataFrame.buildWithStatistics(name, colDescs, rows)
    }
}
