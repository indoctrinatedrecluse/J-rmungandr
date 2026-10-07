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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

class StreamingDataEngineTest {

    @Test
    fun testBufferSlidingWindowCapacity() {
        val engine = StreamingDataEngine(bufferCapacity = 10)
        assertEquals(0, engine.getBufferEvents().size)

        for (i in 1..25) {
            val event = StreamEvent(
                id = i.toLong(),
                timestamp = System.currentTimeMillis(),
                source = "test_src",
                payloadJson = """{"val": $i}""",
                metrics = mapOf("val" to i.toDouble())
            )
            engine.ingestEvent(event)
        }

        val events = engine.getBufferEvents()
        assertEquals(10, events.size, "Buffer should not exceed capacity of 10")
        assertEquals(16L, events.first().id, "Oldest event in sliding window should be id 16")
        assertEquals(25L, events.last().id, "Newest event in sliding window should be id 25")
    }

    @Test
    fun testFreezeToDataFrame() {
        val engine = StreamingDataEngine(bufferCapacity = 50)

        for (i in 1..5) {
            val event = StreamEvent(
                id = i.toLong(),
                timestamp = 1000L + i * 100,
                source = "iot",
                payloadJson = """{"temp": ${20.0 + i}, "vibe": ${50.0 + i}}""",
                metrics = mapOf("temp" to 20.0 + i, "vibe" to 50.0 + i)
            )
            engine.ingestEvent(event)
        }

        val df = engine.freezeToDataFrame()
        assertFalse(df.isEmpty)
        assertEquals(5, df.rowCount)
        assertTrue(df.columns.any { it.name == "event_id" })
        assertTrue(df.columns.any { it.name == "timestamp_ms" })
        assertTrue(df.columns.any { it.name == "source" })
        assertTrue(df.columns.any { it.name == "temp" })
        assertTrue(df.columns.any { it.name == "vibe" })

        val tempVals = df.getColumnValues("temp")
        assertEquals(21.0, tempVals[0])
        assertEquals(25.0, tempVals[4])
    }

    @Test
    fun testEventListener() {
        val engine = StreamingDataEngine(bufferCapacity = 20)
        val counter = AtomicInteger(0)

        val listener: (StreamEvent) -> Unit = { counter.incrementAndGet() }
        engine.addListener(listener)

        engine.ingestEvent(StreamEvent(1L, System.currentTimeMillis(), "src", "{}", emptyMap()))
        engine.ingestEvent(StreamEvent(2L, System.currentTimeMillis(), "src", "{}", emptyMap()))

        assertEquals(2, counter.get())

        engine.removeListener(listener)
        engine.ingestEvent(StreamEvent(3L, System.currentTimeMillis(), "src", "{}", emptyMap()))

        assertEquals(2, counter.get(), "Should not increment after listener removal")
    }
}
