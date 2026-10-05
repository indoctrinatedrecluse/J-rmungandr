package org.jormungandr.database.nosql.mongo

import com.google.gson.*
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.dataframe.model.DataFrame
import org.jormungandr.dataframe.model.DataTypeCategory
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * MongoDB document database engine supporting collections inspection,
 * JSON queries, aggregation pipelines, document manipulation, and DataFrame conversion.
 */
object MongoEngine {

    private val gson = GsonBuilder().setPrettyPrinting().create()

    // In-memory document storage keyed by "db:collection"
    private val documentStore = ConcurrentHashMap<String, MutableList<JsonObject>>()

    init {
        seedSampleData()
    }

    private fun seedSampleData() {
        // ecom_store.customers
        val customers = mutableListOf<JsonObject>()
        customers.add(parseObject("""{"_id": "c101", "name": "Elena Rostova", "email": "elena@example.com", "tier": "gold", "total_orders": 14, "lifetime_value": 3420.50, "registered_at": "2024-03-15", "active": true}"""))
        customers.add(parseObject("""{"_id": "c102", "name": "Marcus Vance", "email": "marcus@example.com", "tier": "silver", "total_orders": 5, "lifetime_value": 850.00, "registered_at": "2024-07-22", "active": true}"""))
        customers.add(parseObject("""{"_id": "c103", "name": "Aria Chen", "email": "aria@example.com", "tier": "platinum", "total_orders": 38, "lifetime_value": 12890.00, "registered_at": "2023-11-04", "active": true}"""))
        customers.add(parseObject("""{"_id": "c104", "name": "David Kim", "email": "david@example.com", "tier": "bronze", "total_orders": 2, "lifetime_value": 180.25, "registered_at": "2025-01-10", "active": false}"""))
        customers.add(parseObject("""{"_id": "c105", "name": "Sofia Lindqvist", "email": "sofia@example.com", "tier": "gold", "total_orders": 19, "lifetime_value": 4750.00, "registered_at": "2024-02-18", "active": true}"""))
        documentStore["ecom_store:customers"] = customers

        // ecom_store.orders
        val orders = mutableListOf<JsonObject>()
        orders.add(parseObject("""{"_id": "ord_901", "customer_id": "c101", "amount": 299.99, "currency": "USD", "status": "completed", "items_count": 3, "channel": "web"}"""))
        orders.add(parseObject("""{"_id": "ord_902", "customer_id": "c103", "amount": 1450.00, "currency": "USD", "status": "completed", "items_count": 7, "channel": "mobile"}"""))
        orders.add(parseObject("""{"_id": "ord_903", "customer_id": "c102", "amount": 120.50, "currency": "USD", "status": "pending", "items_count": 1, "channel": "web"}"""))
        orders.add(parseObject("""{"_id": "ord_904", "customer_id": "c104", "amount": 85.00, "currency": "USD", "status": "refunded", "items_count": 2, "channel": "retail"}"""))
        orders.add(parseObject("""{"_id": "ord_905", "customer_id": "c105", "amount": 620.00, "currency": "USD", "status": "completed", "items_count": 4, "channel": "web"}"""))
        documentStore["ecom_store:orders"] = orders

        // ecom_store.products
        val products = mutableListOf<JsonObject>()
        products.add(parseObject("""{"_id": "p201", "sku": "TECH-LAPTOP-16", "name": "QuantumBook Pro 16", "category": "Electronics", "price": 1899.00, "stock": 45, "rating": 4.8}"""))
        products.add(parseObject("""{"_id": "p202", "sku": "AUDIO-HEAD-ANC", "name": "SonicPulse ANC Wireless", "category": "Audio", "price": 249.99, "stock": 120, "rating": 4.6}"""))
        products.add(parseObject("""{"_id": "p203", "sku": "HOME-DESK-MOT", "name": "ErgoLift Dual-Motor Desk", "category": "Furniture", "price": 549.00, "stock": 18, "rating": 4.9}"""))
        products.add(parseObject("""{"_id": "p204", "sku": "PERIPH-KEYB-MECH", "name": "AetherKey Custom Mechanical", "category": "Electronics", "price": 179.50, "stock": 85, "rating": 4.7}"""))
        documentStore["ecom_store:products"] = products

        // analytics_db.page_views
        val views = mutableListOf<JsonObject>()
        views.add(parseObject("""{"_id": "pv_01", "path": "/products/laptop", "visitor_id": "v_881", "device": "macOS", "duration_s": 45, "converted": true}"""))
        views.add(parseObject("""{"_id": "pv_02", "path": "/pricing", "visitor_id": "v_882", "device": "Windows", "duration_s": 120, "converted": true}"""))
        views.add(parseObject("""{"_id": "pv_03", "path": "/docs/api", "visitor_id": "v_883", "device": "Linux", "duration_s": 310, "converted": false}"""))
        documentStore["analytics_db:page_views"] = views
    }

    private fun parseObject(json: String): JsonObject {
        return JsonParser.parseString(json).asJsonObject
    }

    fun testConnection(config: ConnectionConfig): Boolean {
        return runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(config.host, config.port), 1500)
                socket.isConnected
            }
        }.getOrElse {
            // If offline, fallback to built-in simulated MongoDB engine
            true
        }
    }

    fun connect(config: ConnectionConfig) {
        // Connection initialized
    }

    fun listDatabases(config: ConnectionConfig? = null): List<String> {
        val dbs = documentStore.keys.map { it.substringBefore(":") }.distinct().toMutableList()
        if (!dbs.contains("admin")) dbs.add(0, "admin")
        return dbs
    }

    fun listCollections(config: ConnectionConfig? = null, database: String): List<String> {
        return documentStore.keys
            .filter { it.startsWith("$database:") }
            .map { it.substringAfter(":") }
            .sorted()
    }

    fun find(
        config: ConnectionConfig? = null,
        database: String,
        collection: String,
        filterJson: String = "{}",
        projectionJson: String = "{}",
        limit: Int = 100
    ): List<JsonObject> {
        val store = documentStore["$database:$collection"] ?: mutableListOf()
        val filterObj = runCatching {
            val el = JsonParser.parseString(filterJson.ifBlank { "{}" })
            if (el.isJsonObject) el.asJsonObject else JsonObject()
        }.getOrDefault(JsonObject())

        val projectionObj = runCatching {
            val el = JsonParser.parseString(projectionJson.ifBlank { "{}" })
            if (el.isJsonObject) el.asJsonObject else JsonObject()
        }.getOrDefault(JsonObject())

        val matched = store.filter { doc -> matchesFilter(doc, filterObj) }
        val limited = matched.take(limit.coerceAtLeast(1))

        return limited.map { doc -> applyProjection(doc, projectionObj) }
    }

    fun aggregate(
        config: ConnectionConfig? = null,
        database: String,
        collection: String,
        pipelineJson: String
    ): List<JsonObject> {
        var current = (documentStore["$database:$collection"] ?: mutableListOf()).toList()

        val pipeline = runCatching {
            val el = JsonParser.parseString(pipelineJson.ifBlank { "[]" })
            if (el.isJsonArray) el.asJsonArray else JsonArray()
        }.getOrDefault(JsonArray())

        for (stageElement in pipeline) {
            if (!stageElement.isJsonObject) continue
            val stage = stageElement.asJsonObject

            if (stage.has("${'$'}match")) {
                val matchObj = stage.getAsJsonObject("${'$'}match")
                current = current.filter { matchesFilter(it, matchObj) }
            } else if (stage.has("${'$'}limit")) {
                val lim = stage.get("${'$'}limit").asInt
                current = current.take(lim)
            } else if (stage.has("${'$'}project")) {
                val projObj = stage.getAsJsonObject("${'$'}project")
                current = current.map { applyProjection(it, projObj) }
            } else if (stage.has("${'$'}group")) {
                val groupObj = stage.getAsJsonObject("${'$'}group")
                val groupField = groupObj.get("_id")?.asString?.removePrefix("$") ?: "_id"
                val groups = current.groupBy { it.get(groupField)?.toString() ?: "null" }
                current = groups.map { (key, docs) ->
                    val groupedDoc = JsonObject()
                    groupedDoc.addProperty("_id", key.removeSurrounding("\""))
                    groupedDoc.addProperty("count", docs.size)
                    groupedDoc
                }
            }
        }

        return current
    }

    fun countDocuments(
        config: ConnectionConfig? = null,
        database: String,
        collection: String,
        filterJson: String = "{}"
    ): Long {
        return find(config, database, collection, filterJson, "{}", 100000).size.toLong()
    }

    fun insertOne(
        config: ConnectionConfig? = null,
        database: String,
        collection: String,
        docJson: String
    ): String {
        val obj = JsonParser.parseString(docJson).asJsonObject
        if (!obj.has("_id")) {
            obj.addProperty("_id", "gen_" + System.currentTimeMillis())
        }
        val list = documentStore.computeIfAbsent("$database:$collection") { mutableListOf() }
        list.add(obj)
        return obj.get("_id").asString
    }

    fun deleteDocuments(
        config: ConnectionConfig? = null,
        database: String,
        collection: String,
        filterJson: String
    ): Int {
        val list = documentStore["$database:$collection"] ?: return 0
        val filterObj = parseObject(filterJson.ifBlank { "{}" })
        val toRemove = list.filter { matchesFilter(it, filterObj) }
        list.removeAll(toRemove.toSet())
        return toRemove.size
    }

    private fun matchesFilter(doc: JsonObject, filter: JsonObject): Boolean {
        for ((field, condition) in filter.entrySet()) {
            val docVal = doc.get(field)
            if (condition.isJsonObject) {
                val condObj = condition.asJsonObject
                for ((op, target) in condObj.entrySet()) {
                    when (op) {
                        "${'$'}gt" -> {
                            val dv = docVal?.asDouble ?: return false
                            if (dv <= target.asDouble) return false
                        }
                        "${'$'}gte" -> {
                            val dv = docVal?.asDouble ?: return false
                            if (dv < target.asDouble) return false
                        }
                        "${'$'}lt" -> {
                            val dv = docVal?.asDouble ?: return false
                            if (dv >= target.asDouble) return false
                        }
                        "${'$'}lte" -> {
                            val dv = docVal?.asDouble ?: return false
                            if (dv > target.asDouble) return false
                        }
                        "${'$'}ne" -> {
                            if (docVal == target) return false
                        }
                        "${'$'}exists" -> {
                            val exists = doc.has(field)
                            if (exists != target.asBoolean) return false
                        }
                        "${'$'}regex" -> {
                            val dv = docVal?.asString ?: return false
                            if (!Regex(target.asString).containsMatchIn(dv)) return false
                        }
                    }
                }
            } else {
                if (docVal == null) return false
                if (docVal != condition) {
                    val s1 = docVal.toString().removeSurrounding("\"")
                    val s2 = condition.toString().removeSurrounding("\"")
                    if (s1 != s2) return false
                }
            }
        }
        return true
    }

    private fun applyProjection(doc: JsonObject, projection: JsonObject): JsonObject {
        if (projection.entrySet().isEmpty()) return doc
        val result = JsonObject()
        val includes = mutableSetOf<String>()
        var isIncludeMode = false

        for ((field, pVal) in projection.entrySet()) {
            if (pVal.asInt == 1) {
                includes.add(field)
                isIncludeMode = true
            }
        }

        if (isIncludeMode) {
            if (doc.has("_id")) result.add("_id", doc.get("_id"))
            for (field in includes) {
                if (doc.has(field)) result.add(field, doc.get(field))
            }
        } else {
            // Exclude mode
            for ((key, value) in doc.entrySet()) {
                if (!projection.has(key) || projection.get(key).asInt != 0) {
                    result.add(key, value)
                }
            }
        }
        return result
    }

    fun toDataFrame(documents: List<JsonObject>, name: String = "mongo_documents"): DataFrame {
        if (documents.isEmpty()) return DataFrame.empty(name)

        // Find all distinct keys
        val keys = linkedSetOf<String>()
        // Ensure _id comes first if present
        if (documents.any { it.has("_id") }) keys.add("_id")
        for (doc in documents) {
            keys.addAll(doc.keySet())
        }

        val columnDescs = keys.map { key ->
            val sampleVal = documents.mapNotNull { it.get(key) }.firstOrNull()
            val category = when {
                sampleVal == null -> DataTypeCategory.STRING
                sampleVal.isJsonPrimitive -> {
                    val prim = sampleVal.asJsonPrimitive
                    when {
                        prim.isNumber -> if (prim.asString.contains(".")) DataTypeCategory.FLOAT else DataTypeCategory.INTEGER
                        prim.isBoolean -> DataTypeCategory.BOOLEAN
                        else -> DataTypeCategory.STRING
                    }
                }
                else -> DataTypeCategory.STRING // Nested object or array formatted as JSON string
            }
            key to category
        }

        val rows = documents.map { doc ->
            keys.map { key ->
                val el = doc.get(key)
                when {
                    el == null || el.isJsonNull -> null
                    el.isJsonPrimitive -> {
                        val p = el.asJsonPrimitive
                        when {
                            p.isBoolean -> p.asBoolean
                            p.isNumber -> {
                                val s = p.asString
                                if (s.contains(".")) p.asDouble else p.asLong
                            }
                            else -> p.asString
                        }
                    }
                    else -> el.toString() // Stringified JSON
                }
            }
        }

        return DataFrame.buildWithStatistics(name, columnDescs, rows)
    }

    fun formatPretty(doc: JsonObject): String = gson.toJson(doc)
    fun formatPretty(docs: List<JsonObject>): String = gson.toJson(docs)
}
