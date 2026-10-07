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

package org.jormungandr.database.streaming

import com.google.gson.JsonParser
import org.jormungandr.database.nosql.kafka.KafkaEngine
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.util.Random
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.swing.Timer

enum class StreamSourceType(val displayName: String) {
    IOT_TELEMETRY("🌐 IoT Sensor Telemetry (Temp / Vibe / Power)"),
    FINTECH_PAYMENTS("💳 FinTech Transactions (Amount / Latency / FraudScore)"),
    KAFKA_BROKER("⚡ Kafka Topic Broker Stream (user-events)")
}

data class StreamEvent(
    val id: Long,
    val timestamp: Long,
    val source: String,
    val payloadJson: String,
    val metrics: Map<String, Double>
)

data class LiveStreamStats(
    val totalEventsIngested: Long,
    val eventsPerSecond: Double,
    val bufferSize: Int,
    val metricAverages: Map<String, Double>
)

/**
 * Real-Time Streaming Data & Timeseries Engine.
 * Features:
 * - Thread-safe sliding memory window buffer (configurable 50..2000 events)
 * - Multi-source event ingress (IoT sensors, FinTech payments, Kafka topic stream)
 * - Real-time continuous rolling metrics and throughput telemetry
 * - "Freeze to DataFrame": Instant snapshot conversion to native DataFrame for SQL and ML modeling.
 */
class StreamingDataEngine(
    var bufferCapacity: Int = 300
) {

    private val eventSequence = AtomicLong(1L)
    private val buffer = ConcurrentLinkedDeque<StreamEvent>()
    private val listeners = CopyOnWriteArrayList<(StreamEvent) -> Unit>()
    private val isRunning = AtomicBoolean(false)
    private val random = Random(42L)

    private var activeSource = StreamSourceType.IOT_TELEMETRY
    private var streamTimer: Timer? = null

    // Throughput tracking
    private var lastSecondTimestamp = System.currentTimeMillis()
    private var eventsSinceLastSecond = 0
    private var currentEventsPerSecond = 0.0

    fun addListener(listener: (StreamEvent) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (StreamEvent) -> Unit) {
        listeners.remove(listener)
    }

    fun isStreaming(): Boolean = isRunning.get()

    fun startStream(source: StreamSourceType = activeSource, intervalMs: Int = 100) {
        if (isRunning.get()) stopStream()
        activeSource = source
        isRunning.set(true)

        streamTimer = Timer(intervalMs) {
            if (!isRunning.get()) return@Timer
            generateTick()
        }.apply {
            start()
        }
    }

    fun stopStream() {
        isRunning.set(false)
        streamTimer?.stop()
        streamTimer = null
    }

    fun clearBuffer() {
        buffer.clear()
    }

    fun getBufferEvents(): List<StreamEvent> = buffer.toList()

    fun ingestEvent(event: StreamEvent) {
        buffer.add(event)
        while (buffer.size > bufferCapacity) {
            buffer.pollFirst()
        }

        // Track throughput
        eventsSinceLastSecond++
        val now = System.currentTimeMillis()
        if (now - lastSecondTimestamp >= 1000) {
            currentEventsPerSecond = eventsSinceLastSecond * 1000.0 / (now - lastSecondTimestamp)
            eventsSinceLastSecond = 0
            lastSecondTimestamp = now
        }

        for (l in listeners) {
            runCatching { l.invoke(event) }
        }
    }

    private fun generateTick() {
        val now = System.currentTimeMillis()
        val id = eventSequence.getAndIncrement()

        when (activeSource) {
            StreamSourceType.IOT_TELEMETRY -> {
                val temp = 20.0 + random.nextGaussian() * 3.5
                val vibe = 50.0 + random.nextDouble() * 25.0 + (if (random.nextDouble() > 0.95) 40.0 else 0.0)
                val power = 3.2 + random.nextGaussian() * 0.4
                val sensorId = "sensor_${(id % 8) + 1}"

                val json = """{"sensor_id":"$sensorId","temp_c":${String.format("%.2f", temp)},"vibe_hz":${String.format("%.2f", vibe)},"power_kw":${String.format("%.2f", power)}}"""
                val metrics = mapOf("temp_c" to temp, "vibe_hz" to vibe, "power_kw" to power)
                ingestEvent(StreamEvent(id, now, "iot-telemetry", json, metrics))
            }
            StreamSourceType.FINTECH_PAYMENTS -> {
                val amount = 10.0 + random.nextDouble() * 500.0
                val latency = 12.0 + random.nextGaussian() * 4.0
                val fraudScore = random.nextDouble().let { if (it > 0.92) 0.85 + random.nextDouble() * 0.15 else it * 0.3 }
                val status = if (fraudScore > 0.8) "FLAGGED" else "APPROVED"

                val json = """{"tx_id":"tx_$id","amount":${String.format("%.2f", amount)},"latency_ms":${String.format("%.1f", latency)},"fraud_score":${String.format("%.3f", fraudScore)},"status":"$status"}"""
                val metrics = mapOf("amount" to amount, "latency_ms" to latency, "fraud_score" to fraudScore)
                ingestEvent(StreamEvent(id, now, "payment-stream", json, metrics))
            }
            StreamSourceType.KAFKA_BROKER -> {
                val kafkaRecords = KafkaEngine.consume("user-events", maxRecords = 1)
                val rec = kafkaRecords.firstOrNull()
                val latency = 8.0 + random.nextDouble() * 10.0
                val cartVal = 100.0 + random.nextDouble() * 200.0
                val json = rec?.value ?: """{"event":"click","user_id":1001,"cart_val":${String.format("%.2f", cartVal)}}"""
                val metrics = mapOf("latency_ms" to latency, "cart_val" to cartVal)
                ingestEvent(StreamEvent(id, now, "kafka://user-events", json, metrics))
            }
        }
    }

    fun getStats(): LiveStreamStats {
        val events = buffer.toList()
        val allMetricKeys = events.flatMap { it.metrics.keys }.toSet()
        val avgs = allMetricKeys.associateWith { key ->
            val vals = events.mapNotNull { it.metrics[key] }
            if (vals.isNotEmpty()) vals.average() else 0.0
        }

        return LiveStreamStats(
            totalEventsIngested = eventSequence.get() - 1,
            eventsPerSecond = currentEventsPerSecond,
            bufferSize = events.size,
            metricAverages = avgs
        )
    }

    /**
     * Snapshots the active sliding window buffer into a native immutable DataFrame.
     */
    fun freezeToDataFrame(): DataFrame {
        val events = buffer.toList()
        if (events.isEmpty()) return DataFrame.empty()

        val allMetricKeys = events.flatMap { it.metrics.keys }.distinct()
        val cols = listOf(
            Pair("event_id", DataTypeCategory.INTEGER),
            Pair("timestamp_ms", DataTypeCategory.INTEGER),
            Pair("source", DataTypeCategory.STRING)
        ) + allMetricKeys.map { Pair(it, DataTypeCategory.FLOAT) }

        val rows = events.map { e ->
            listOf(e.id, e.timestamp, e.source) + allMetricKeys.map { k -> e.metrics[k] ?: 0.0 }
        }

        return DataFrame.buildWithStatistics("streaming_snapshot", cols, rows)
    }
}
