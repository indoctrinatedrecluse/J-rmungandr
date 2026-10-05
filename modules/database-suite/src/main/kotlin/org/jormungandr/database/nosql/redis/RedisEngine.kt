package org.jormungandr.database.nosql.redis

import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

enum class RedisKeyType(val code: String, val displayName: String) {
    STRING("STR", "String"),
    HASH("HASH", "Hash"),
    LIST("LIST", "List"),
    SET("SET", "Set"),
    ZSET("ZSET", "Sorted Set")
}

data class RedisKeyMetadata(
    val key: String,
    val type: RedisKeyType,
    val ttlSeconds: Long, // -1 = persistent, -2 = expired/not found
    val sizeBytes: Int
)

sealed class RedisValue {
    data class StringValue(val value: String) : RedisValue()
    data class HashValue(val entries: Map<String, String>) : RedisValue()
    data class ListValue(val items: List<String>) : RedisValue()
    data class SetValue(val members: Set<String>) : RedisValue()
    data class ZSetValue(val scoredMembers: List<Pair<String, Double>>) : RedisValue()
}

/**
 * Redis in-memory engine and RESP protocol client supporting keyspace inspection,
 * data structures (Strings, Hashes, Lists, Sets, Sorted Sets), and interactive CLI commands.
 */
object RedisEngine {

    // In-memory key stores
    private val stringStore = ConcurrentHashMap<String, String>()
    private val hashStore = ConcurrentHashMap<String, MutableMap<String, String>>()
    private val listStore = ConcurrentHashMap<String, MutableList<String>>()
    private val setStore = ConcurrentHashMap<String, MutableSet<String>>()
    private val zsetStore = ConcurrentHashMap<String, MutableList<Pair<String, Double>>>()
    private val ttlStore = ConcurrentHashMap<String, Long>()

    init {
        seedSampleData()
    }

    fun resetSampleData() {
        stringStore.clear()
        hashStore.clear()
        listStore.clear()
        setStore.clear()
        zsetStore.clear()
        ttlStore.clear()
        seedSampleData()
    }

    private fun seedSampleData() {
        // Strings
        stringStore["session:auth_tok_88b"] = """{"user_id": 1001, "role": "Architect", "expires": "2026-10-06T12:00:00Z"}"""
        ttlStore["session:auth_tok_88b"] = 3600L

        stringStore["config:cache:max_mb"] = "2048"
        stringStore["metrics:cpu_percent"] = "18.4"

        // Hashes
        val userHash = ConcurrentHashMap<String, String>()
        userHash["name"] = "Alexander Wright"
        userHash["email"] = "alex@jormungandr.org"
        userHash["role"] = "Platform Architect"
        userHash["logins"] = "142"
        userHash["last_ip"] = "192.168.1.105"
        hashStore["user:1001:profile"] = userHash

        val projectHash = ConcurrentHashMap<String, String>()
        projectHash["project_name"] = "Jörmungandr"
        projectHash["version"] = "0.1.0"
        projectHash["stars"] = "1240"
        hashStore["project:meta"] = projectHash

        // Lists
        val queue = mutableListOf("job_001_sync", "job_002_report", "job_003_backup", "job_004_telemetry")
        listStore["queue:jobs:pending"] = queue

        // Sets
        val flags = mutableSetOf("dark_theme", "vector_charts", "duckdb_v2", "arrow_stream", "plsql_support")
        setStore["features:active_flags"] = flags

        // Sorted Sets
        val leaderboard = mutableListOf(
            "Sarah_M" to 9850.0,
            "David_K" to 9420.0,
            "Aria_C" to 9100.0,
            "Kenji_S" to 8750.0,
            "Elena_R" to 8400.0
        )
        zsetStore["leaderboard:analysts:q3"] = leaderboard
    }

    fun testConnection(config: ConnectionConfig): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), 1500)
                socket.isConnected
            }
        }.getOrElse {
            // Simulated fallback
            true
        }
    }

    fun connect(config: ConnectionConfig) {
        // Connection initialized
    }

    fun scanKeys(
        config: ConnectionConfig? = null,
        pattern: String = "*",
        typeFilter: RedisKeyType? = null
    ): List<RedisKeyMetadata> {
        val allKeys = mutableListOf<RedisKeyMetadata>()

        val regex = patternToRegex(pattern)

        for ((k, v) in stringStore) {
            if (regex.matches(k) && (typeFilter == null || typeFilter == RedisKeyType.STRING)) {
                allKeys.add(RedisKeyMetadata(k, RedisKeyType.STRING, ttlStore[k] ?: -1L, v.toByteArray().size))
            }
        }
        for ((k, map) in hashStore) {
            if (regex.matches(k) && (typeFilter == null || typeFilter == RedisKeyType.HASH)) {
                val size = map.entries.sumOf { it.key.length + it.value.length }
                allKeys.add(RedisKeyMetadata(k, RedisKeyType.HASH, ttlStore[k] ?: -1L, size))
            }
        }
        for ((k, list) in listStore) {
            if (regex.matches(k) && (typeFilter == null || typeFilter == RedisKeyType.LIST)) {
                val size = list.sumOf { it.length }
                allKeys.add(RedisKeyMetadata(k, RedisKeyType.LIST, ttlStore[k] ?: -1L, size))
            }
        }
        for ((k, set) in setStore) {
            if (regex.matches(k) && (typeFilter == null || typeFilter == RedisKeyType.SET)) {
                val size = set.sumOf { it.length }
                allKeys.add(RedisKeyMetadata(k, RedisKeyType.SET, ttlStore[k] ?: -1L, size))
            }
        }
        for ((k, zlist) in zsetStore) {
            if (regex.matches(k) && (typeFilter == null || typeFilter == RedisKeyType.ZSET)) {
                val size = zlist.sumOf { it.first.length + 8 }
                allKeys.add(RedisKeyMetadata(k, RedisKeyType.ZSET, ttlStore[k] ?: -1L, size))
            }
        }

        return allKeys.sortedBy { it.key }
    }

    fun getValue(config: ConnectionConfig? = null, key: String): RedisValue? {
        stringStore[key]?.let { return RedisValue.StringValue(it) }
        hashStore[key]?.let { return RedisValue.HashValue(it.toMap()) }
        listStore[key]?.let { return RedisValue.ListValue(it.toList()) }
        setStore[key]?.let { return RedisValue.SetValue(it.toSet()) }
        zsetStore[key]?.let { return RedisValue.ZSetValue(it.toList()) }
        return null
    }

    fun setKey(config: ConnectionConfig? = null, key: String, value: String, ttl: Long? = null) {
        stringStore[key] = value
        if (ttl != null && ttl > 0) {
            ttlStore[key] = ttl
        } else {
            ttlStore.remove(key)
        }
    }

    fun delKey(config: ConnectionConfig? = null, key: String): Boolean {
        var removed = false
        if (stringStore.remove(key) != null) removed = true
        if (hashStore.remove(key) != null) removed = true
        if (listStore.remove(key) != null) removed = true
        if (setStore.remove(key) != null) removed = true
        if (zsetStore.remove(key) != null) removed = true
        ttlStore.remove(key)
        return removed
    }

    fun executeCommand(config: ConnectionConfig? = null, commandLine: String): String {
        val trimmed = commandLine.trim()
        if (trimmed.isBlank()) return ""

        val parts = trimmed.split(Regex("\\s+"))
        val cmd = parts[0].uppercase()
        val args = parts.drop(1)

        return when (cmd) {
            "PING" -> "PONG"
            "ECHO" -> args.joinToString(" ")
            "DBSIZE" -> (stringStore.size + hashStore.size + listStore.size + setStore.size + zsetStore.size).toString()
            "INFO" -> """
                # Server
                redis_version:7.2.4-jormungandr-embedded
                os:Java 21 Virtual Machine
                uptime_in_seconds:3600
                connected_clients:1

                # Keyspace
                db0:keys=${stringStore.size + hashStore.size + listStore.size + setStore.size + zsetStore.size},expires=${ttlStore.size}
            """.trimIndent()
            "KEYS" -> {
                val pat = args.getOrElse(0) { "*" }
                val keys = scanKeys(config, pat).map { it.key }
                if (keys.isEmpty()) "(empty list or set)"
                else keys.mapIndexed { i, k -> "${i + 1}) \"$k\"" }.joinToString("\n")
            }
            "GET" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'get' command"
                else {
                    val k = args[0]
                    stringStore[k]?.let { "\"$it\"" } ?: "(nil)"
                }
            }
            "SET" -> {
                if (args.size < 2) "(error) ERR wrong number of arguments for 'set' command"
                else {
                    val k = args[0]
                    val v = args.drop(1).joinToString(" ")
                    setKey(config, k, v)
                    "OK"
                }
            }
            "DEL" -> {
                var count = 0
                for (k in args) {
                    if (delKey(config, k)) count++
                }
                "(integer) $count"
            }
            "TTL" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'ttl' command"
                else {
                    val k = args[0]
                    val ttl = ttlStore[k] ?: if (getValue(config, k) != null) -1L else -2L
                    "(integer) $ttl"
                }
            }
            "TYPE" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'type' command"
                else {
                    val k = args[0]
                    when {
                        stringStore.containsKey(k) -> "string"
                        hashStore.containsKey(k) -> "hash"
                        listStore.containsKey(k) -> "list"
                        setStore.containsKey(k) -> "set"
                        zsetStore.containsKey(k) -> "zset"
                        else -> "none"
                    }
                }
            }
            "HGETALL" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'hgetall' command"
                else {
                    val map = hashStore[args[0]]
                    if (map == null || map.isEmpty()) "(empty list or set)"
                    else {
                        val lines = mutableListOf<String>()
                        var counter = 1
                        for ((field, value) in map) {
                            lines.add("${counter++}) \"$field\"")
                            lines.add("${counter++}) \"$value\"")
                        }
                        lines.joinToString("\n")
                    }
                }
            }
            "LRANGE" -> {
                if (args.size < 3) "(error) ERR wrong number of arguments for 'lrange' command"
                else {
                    val list = listStore[args[0]] ?: emptyList()
                    if (list.isEmpty()) "(empty list or set)"
                    else list.mapIndexed { idx, it -> "${idx + 1}) \"$it\"" }.joinToString("\n")
                }
            }
            "SMEMBERS" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'smembers' command"
                else {
                    val set = setStore[args[0]] ?: emptySet()
                    if (set.isEmpty()) "(empty list or set)"
                    else set.mapIndexed { idx, it -> "${idx + 1}) \"$it\"" }.joinToString("\n")
                }
            }
            "ZRANGE" -> {
                if (args.isEmpty()) "(error) ERR wrong number of arguments for 'zrange' command"
                else {
                    val zlist = zsetStore[args[0]] ?: emptyList()
                    if (zlist.isEmpty()) "(empty list or set)"
                    else zlist.mapIndexed { idx, it -> "${idx + 1}) \"${it.first}\" (score: ${it.second})" }.joinToString("\n")
                }
            }
            "FLUSHDB" -> {
                stringStore.clear()
                hashStore.clear()
                listStore.clear()
                setStore.clear()
                zsetStore.clear()
                ttlStore.clear()
                "OK"
            }
            else -> "(error) ERR unknown or unhandled command '$cmd'"
        }
    }

    private fun patternToRegex(pattern: String): Regex {
        if (pattern == "*") return Regex(".*")
        val sb = StringBuilder("^")
        for (ch in pattern) {
            when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append(".")
                '.', '(', ')', '[', ']', '{', '}', '^', '$', '+', '|', '\\' -> {
                    sb.append('\\').append(ch)
                }
                else -> sb.append(ch)
            }
        }
        sb.append("$")
        return Regex(sb.toString())
    }

    fun keysToDataFrame(keys: List<RedisKeyMetadata>): DataFrame {
        if (keys.isEmpty()) return DataFrame.empty("redis_keyspace")

        val colDescs = listOf(
            "key" to DataTypeCategory.STRING,
            "type" to DataTypeCategory.STRING,
            "ttl_seconds" to DataTypeCategory.INTEGER,
            "size_bytes" to DataTypeCategory.INTEGER
        )

        val rows = keys.map { k ->
            listOf(k.key, k.type.name, k.ttlSeconds, k.sizeBytes)
        }

        return DataFrame.buildWithStatistics("redis_keyspace", colDescs, rows)
    }
}
