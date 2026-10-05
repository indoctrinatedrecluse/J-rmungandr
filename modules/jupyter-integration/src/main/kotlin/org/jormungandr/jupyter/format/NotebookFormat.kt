package org.jormungandr.jupyter.format

import com.google.gson.*
import org.jormungandr.jupyter.model.*
import java.io.File

/**
 * Parser and serializer for Jupyter Notebook v4 (.ipynb) format.
 * Accurately parses cells, source arrays, rich outputs, and cell comment metadata.
 */
object NotebookFormat {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Parses a notebook JSON string into a [NotebookModel].
     */
    fun readNotebook(jsonString: String): NotebookModel {
        if (jsonString.isBlank()) {
            return NotebookModel.createDefaultPythonNotebook()
        }

        val root = try {
            JsonParser.parseString(jsonString).asJsonObject
        } catch (e: Exception) {
            return NotebookModel.createDefaultPythonNotebook()
        }

        val nbformat = root.get("nbformat")?.asInt ?: 4
        val nbformatMinor = root.get("nbformat_minor")?.asInt ?: 5

        // Parse metadata
        val metadata = NotebookMetadata()
        if (root.has("metadata") && root.get("metadata").isJsonObject) {
            val metaObj = root.getAsJsonObject("metadata")
            if (metaObj.has("kernelspec") && metaObj.get("kernelspec").isJsonObject) {
                val ks = metaObj.getAsJsonObject("kernelspec")
                metadata.kernelspec = KernelSpecMetadata(
                    name = ks.get("name")?.asString ?: "python3",
                    displayName = ks.get("display_name")?.asString ?: "Python 3",
                    language = ks.get("language")?.asString ?: "python"
                )
            }
            if (metaObj.has("language_info") && metaObj.get("language_info").isJsonObject) {
                val li = metaObj.getAsJsonObject("language_info")
                metadata.languageInfo = LanguageInfoMetadata(
                    name = li.get("name")?.asString ?: "python",
                    version = li.get("version")?.asString ?: "3.x",
                    pygmentsLexer = li.get("pygments_lexer")?.asString ?: "ipython3",
                    fileExtension = li.get("file_extension")?.asString ?: ".py"
                )
            }
            for ((key, elem) in metaObj.entrySet()) {
                if (key != "kernelspec" && key != "language_info") {
                    metadata.custom[key] = jsonElementToRaw(elem)
                }
            }
        }

        // Parse cells
        val cells = mutableListOf<NotebookCell>()
        if (root.has("cells") && root.get("cells").isJsonArray) {
            val cellArray = root.getAsJsonArray("cells")
            for (cellElem in cellArray) {
                if (!cellElem.isJsonObject) continue
                val cellObj = cellElem.asJsonObject

                val id = cellObj.get("id")?.asString ?: java.util.UUID.randomUUID().toString()
                val typeStr = cellObj.get("cell_type")?.asString ?: "code"
                val cellType = CellType.fromString(typeStr)
                val source = parseSource(cellObj.get("source"))
                val executionCount = if (cellObj.has("execution_count") && !cellObj.get("execution_count").isJsonNull) {
                    cellObj.get("execution_count")?.asInt
                } else null

                // Metadata and Comments
                val cellMeta = mutableMapOf<String, Any>()
                val comments = mutableListOf<CellComment>()
                if (cellObj.has("metadata") && cellObj.get("metadata").isJsonObject) {
                    val m = cellObj.getAsJsonObject("metadata")
                    for ((k, v) in m.entrySet()) {
                        if (k == "comments" && v.isJsonArray) {
                            comments.addAll(parseComments(v.asJsonArray))
                        } else {
                            cellMeta[k] = jsonElementToRaw(v)
                        }
                    }
                }

                // Outputs
                val outputs = mutableListOf<CellOutput>()
                if (cellObj.has("outputs") && cellObj.get("outputs").isJsonArray) {
                    for (outElem in cellObj.getAsJsonArray("outputs")) {
                        if (!outElem.isJsonObject) continue
                        val out = parseCellOutput(outElem.asJsonObject)
                        if (out != null) outputs.add(out)
                    }
                }

                cells.add(
                    NotebookCell(
                        id = id,
                        cellType = cellType,
                        source = source,
                        executionCount = executionCount,
                        outputs = outputs,
                        comments = comments,
                        metadata = cellMeta
                    )
                )
            }
        }

        if (cells.isEmpty()) {
            cells.add(NotebookCell(cellType = CellType.CODE, source = ""))
        }

        return NotebookModel(
            cells = cells,
            metadata = metadata,
            nbformat = nbformat,
            nbformatMinor = nbformatMinor
        )
    }

    /**
     * Reads a notebook from a [File].
     */
    fun readNotebook(file: File): NotebookModel {
        return readNotebook(file.readText(Charsets.UTF_8))
    }

    /**
     * Serializes a [NotebookModel] into standard .ipynb JSON format.
     */
    fun writeNotebook(model: NotebookModel): String {
        val root = JsonObject()
        root.addProperty("nbformat", model.nbformat)
        root.addProperty("nbformat_minor", model.nbformatMinor)

        // Metadata
        val metaObj = JsonObject()
        val ks = JsonObject().apply {
            addProperty("name", model.metadata.kernelspec.name)
            addProperty("display_name", model.metadata.kernelspec.displayName)
            addProperty("language", model.metadata.kernelspec.language)
        }
        metaObj.add("kernelspec", ks)

        val li = JsonObject().apply {
            addProperty("name", model.metadata.languageInfo.name)
            addProperty("version", model.metadata.languageInfo.version)
            addProperty("pygments_lexer", model.metadata.languageInfo.pygmentsLexer)
            addProperty("file_extension", model.metadata.languageInfo.fileExtension)
        }
        metaObj.add("language_info", li)

        for ((k, v) in model.metadata.custom) {
            metaObj.add(k, rawToJsonElement(v))
        }
        root.add("metadata", metaObj)

        // Cells
        val cellArray = JsonArray()
        for (cell in model.cells) {
            val cellObj = JsonObject()
            cellObj.addProperty("id", cell.id)
            cellObj.addProperty("cell_type", cell.cellType.value)

            // Split multiline source into array of lines for canonical nbformat
            val lines = cell.source.split("\n")
            val sourceArray = JsonArray()
            for (i in lines.indices) {
                val line = if (i < lines.size - 1) lines[i] + "\n" else lines[i]
                sourceArray.add(line)
            }
            cellObj.add("source", sourceArray)

            // Cell Metadata including Comments
            val cellMeta = JsonObject()
            for ((k, v) in cell.metadata) {
                cellMeta.add(k, rawToJsonElement(v))
            }
            if (cell.comments.isNotEmpty()) {
                cellMeta.add("comments", serializeComments(cell.comments))
            }
            cellObj.add("metadata", cellMeta)

            if (cell.cellType == CellType.CODE) {
                if (cell.executionCount != null) {
                    cellObj.addProperty("execution_count", cell.executionCount)
                } else {
                    cellObj.add("execution_count", JsonNull.INSTANCE)
                }

                val outArray = JsonArray()
                for (output in cell.outputs) {
                    outArray.add(serializeCellOutput(output))
                }
                cellObj.add("outputs", outArray)
            }

            cellArray.add(cellObj)
        }
        root.add("cells", cellArray)

        return gson.toJson(root)
    }

    /**
     * Writes a [NotebookModel] directly to a [File].
     */
    fun writeNotebook(model: NotebookModel, file: File) {
        file.writeText(writeNotebook(model), Charsets.UTF_8)
    }

    private fun parseSource(elem: JsonElement?): String {
        if (elem == null || elem.isJsonNull) return ""
        return if (elem.isJsonArray) {
            elem.asJsonArray.joinToString("") { it.asString }
        } else {
            elem.asString
        }
    }

    private fun parseComments(array: JsonArray): List<CellComment> {
        val result = mutableListOf<CellComment>()
        for (item in array) {
            if (!item.isJsonObject) continue
            val obj = item.asJsonObject
            val id = obj.get("id")?.asString ?: java.util.UUID.randomUUID().toString()
            val author = obj.get("author")?.asString ?: "Anonymous"
            val text = obj.get("text")?.asString ?: ""
            val timestamp = obj.get("timestamp")?.asLong ?: System.currentTimeMillis()
            val resolved = obj.get("resolved")?.asBoolean ?: false

            val replies = mutableListOf<CellCommentReply>()
            if (obj.has("replies") && obj.get("replies").isJsonArray) {
                for (repElem in obj.getAsJsonArray("replies")) {
                    if (!repElem.isJsonObject) continue
                    val repObj = repElem.asJsonObject
                    replies.add(
                        CellCommentReply(
                            id = repObj.get("id")?.asString ?: java.util.UUID.randomUUID().toString(),
                            author = repObj.get("author")?.asString ?: "Anonymous",
                            text = repObj.get("text")?.asString ?: "",
                            timestamp = repObj.get("timestamp")?.asLong ?: System.currentTimeMillis()
                        )
                    )
                }
            }

            result.add(
                CellComment(
                    id = id,
                    author = author,
                    text = text,
                    timestamp = timestamp,
                    resolved = resolved,
                    replies = replies
                )
            )
        }
        return result
    }

    private fun serializeComments(comments: List<CellComment>): JsonArray {
        val array = JsonArray()
        for (comment in comments) {
            val obj = JsonObject().apply {
                addProperty("id", comment.id)
                addProperty("author", comment.author)
                addProperty("text", comment.text)
                addProperty("timestamp", comment.timestamp)
                addProperty("resolved", comment.resolved)
                val repArray = JsonArray()
                for (reply in comment.replies) {
                    val repObj = JsonObject().apply {
                        addProperty("id", reply.id)
                        addProperty("author", reply.author)
                        addProperty("text", reply.text)
                        addProperty("timestamp", reply.timestamp)
                    }
                    repArray.add(repObj)
                }
                add("replies", repArray)
            }
            array.add(obj)
        }
        return array
    }

    private fun parseCellOutput(obj: JsonObject): CellOutput? {
        val type = obj.get("output_type")?.asString ?: return null
        return when (type) {
            "stream" -> {
                val name = obj.get("name")?.asString ?: "stdout"
                val text = parseSource(obj.get("text"))
                CellOutput.StreamOutput(name, text)
            }
            "execute_result" -> {
                val execCount = obj.get("execution_count")?.asInt ?: 1
                val data = parseDataBundle(obj.getAsJsonObject("data"))
                val meta = parseDataBundle(obj.getAsJsonObject("metadata"))
                CellOutput.ExecuteResultOutput(execCount, data, meta)
            }
            "display_data" -> {
                val data = parseDataBundle(obj.getAsJsonObject("data"))
                val meta = parseDataBundle(obj.getAsJsonObject("metadata"))
                CellOutput.DisplayDataOutput(data, meta)
            }
            "error" -> {
                val ename = obj.get("ename")?.asString ?: "Error"
                val evalue = obj.get("evalue")?.asString ?: ""
                val tb = mutableListOf<String>()
                if (obj.has("traceback") && obj.get("traceback").isJsonArray) {
                    for (line in obj.getAsJsonArray("traceback")) {
                        tb.add(line.asString)
                    }
                }
                CellOutput.ErrorOutput(ename, evalue, tb)
            }
            else -> null
        }
    }

    private fun serializeCellOutput(output: CellOutput): JsonObject {
        val obj = JsonObject()
        when (output) {
            is CellOutput.StreamOutput -> {
                obj.addProperty("output_type", "stream")
                obj.addProperty("name", output.name)
                val lines = output.text.split("\n")
                val textArray = JsonArray()
                for (i in lines.indices) {
                    val line = if (i < lines.size - 1) lines[i] + "\n" else lines[i]
                    textArray.add(line)
                }
                obj.add("text", textArray)
            }
            is CellOutput.ExecuteResultOutput -> {
                obj.addProperty("output_type", "execute_result")
                obj.addProperty("execution_count", output.executionCount)
                obj.add("data", rawToJsonElement(output.data))
                obj.add("metadata", rawToJsonElement(output.metadata))
            }
            is CellOutput.DisplayDataOutput -> {
                obj.addProperty("output_type", "display_data")
                obj.add("data", rawToJsonElement(output.data))
                obj.add("metadata", rawToJsonElement(output.metadata))
            }
            is CellOutput.ErrorOutput -> {
                obj.addProperty("output_type", "error")
                obj.addProperty("ename", output.ename)
                obj.addProperty("evalue", output.evalue)
                val tbArray = JsonArray()
                output.traceback.forEach { tbArray.add(it) }
                obj.add("traceback", tbArray)
            }
        }
        return obj
    }

    private fun parseDataBundle(obj: JsonObject?): Map<String, Any> {
        if (obj == null) return emptyMap()
        val result = mutableMapOf<String, Any>()
        for ((k, v) in obj.entrySet()) {
            result[k] = jsonElementToRaw(v)
        }
        return result
    }

    private fun jsonElementToRaw(elem: JsonElement): Any {
        return when {
            elem.isJsonPrimitive -> {
                val prim = elem.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isNumber -> prim.asNumber
                    else -> prim.asString
                }
            }
            elem.isJsonArray -> {
                elem.asJsonArray.map { jsonElementToRaw(it) }
            }
            elem.isJsonObject -> {
                elem.asJsonObject.entrySet().associate { it.key to jsonElementToRaw(it.value) }
            }
            else -> ""
        }
    }

    private fun rawToJsonElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull.INSTANCE
            is String -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is Boolean -> JsonPrimitive(value)
            is List<*> -> {
                val arr = JsonArray()
                value.forEach { arr.add(rawToJsonElement(it)) }
                arr
            }
            is Map<*, *> -> {
                val obj = JsonObject()
                value.forEach { (k, v) -> obj.add(k.toString(), rawToJsonElement(v)) }
                obj
            }
            else -> JsonPrimitive(value.toString())
        }
    }
}
