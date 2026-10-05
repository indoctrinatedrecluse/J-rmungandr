package org.jormungandr.database.nosql.redis

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RedisEngineTest {

    @BeforeEach
    fun setUp() {
        RedisEngine.resetSampleData()
    }

    @Test
    fun `test scan keys and pattern filtering`() {
        val allKeys = RedisEngine.scanKeys(pattern = "*")
        assertTrue(allKeys.isNotEmpty())

        val userKeys = RedisEngine.scanKeys(pattern = "user:*")
        assertTrue(userKeys.any { it.key == "user:1001:profile" })

        val stringKeys = RedisEngine.scanKeys(pattern = "*", typeFilter = RedisKeyType.STRING)
        assertTrue(stringKeys.all { it.type == RedisKeyType.STRING })
    }

    @Test
    fun `test data structure value inspection`() {
        val stringVal = RedisEngine.getValue(key = "config:cache:max_mb")
        assertTrue(stringVal is RedisValue.StringValue)
        assertEquals("2048", (stringVal as RedisValue.StringValue).value)

        val hashVal = RedisEngine.getValue(key = "user:1001:profile")
        assertTrue(hashVal is RedisValue.HashValue)
        assertEquals("Alexander Wright", (hashVal as RedisValue.HashValue).entries["name"])

        val listVal = RedisEngine.getValue(key = "queue:jobs:pending")
        assertTrue(listVal is RedisValue.ListValue)
        assertTrue((listVal as RedisValue.ListValue).items.contains("job_001_sync"))

        val setVal = RedisEngine.getValue(key = "features:active_flags")
        assertTrue(setVal is RedisValue.SetValue)
        assertTrue((setVal as RedisValue.SetValue).members.contains("dark_theme"))

        val zsetVal = RedisEngine.getValue(key = "leaderboard:analysts:q3")
        assertTrue(zsetVal is RedisValue.ZSetValue)
        assertTrue((zsetVal as RedisValue.ZSetValue).scoredMembers.any { it.first == "Sarah_M" })
    }

    @Test
    fun `test execute cli commands`() {
        assertEquals("PONG", RedisEngine.executeCommand(commandLine = "PING"))
        assertEquals("OK", RedisEngine.executeCommand(commandLine = "SET test:key:123 HelloRedis"))
        assertEquals("\"HelloRedis\"", RedisEngine.executeCommand(commandLine = "GET test:key:123"))
        assertEquals("(integer) 1", RedisEngine.executeCommand(commandLine = "DEL test:key:123"))
        assertEquals("(nil)", RedisEngine.executeCommand(commandLine = "GET test:key:123"))
    }

    @Test
    fun `test keys to dataframe`() {
        val keys = RedisEngine.scanKeys()
        val df = RedisEngine.keysToDataFrame(keys)
        assertEquals(keys.size, df.rowCount)
        assertEquals(4, df.columns.size)
        assertTrue(df.columns.any { it.name == "key" })
        assertTrue(df.columns.any { it.name == "type" })
    }
}
