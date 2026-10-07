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

package org.jormungandr.jupyter.dag

import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookCell
import org.jormungandr.jupyter.model.NotebookModel

/**
 * Directed edge representing a variable dependency between two cells.
 */
data class DependencyEdge(
    val upstreamCellId: String,
    val downstreamCellId: String,
    val variable: String
)

/**
 * Static variable analysis for an individual notebook code cell.
 */
data class CellVariableAnalysis(
    val cellId: String,
    val cellIndex: Int,
    val executionCount: Int?,
    val definedVariables: Set<String>,
    val usedVariables: Set<String>,
    val upstreamCellIds: Set<String> = emptySet(),
    val downstreamCellIds: Set<String> = emptySet(),
    val isStale: Boolean = false
)

/**
 * Complete Directed Acyclic Graph (DAG) of notebook cell execution and variable dependencies.
 */
data class NotebookDependencyGraph(
    val cellAnalyses: Map<String, CellVariableAnalysis>,
    val edges: List<DependencyEdge>,
    val topologicalOrder: List<String>,
    val hasCycles: Boolean,
    val staleCellIds: Set<String>
) {
    fun getDownstreamCascade(cellId: String): List<String> {
        val visited = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(cellId)

        while (queue.isNotEmpty()) {
            val curr = queue.removeFirst()
            val analysis = cellAnalyses[curr] ?: continue
            for (downstream in analysis.downstreamCellIds) {
                if (downstream !in visited && downstream != cellId) {
                    visited.add(downstream)
                    queue.add(downstream)
                }
            }
        }

        // Return downstream in topological order
        return topologicalOrder.filter { it in visited }
    }
}

/**
 * Service that analyzes Python code cells to build variable dependency graphs,
 * compute execution DAGs, detect stale outputs, and calculate topological cascades.
 */
object NotebookDependencyGraphService {

    private val PYTHON_KEYWORDS = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
        "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
    )

    private val PYTHON_BUILTINS = setOf(
        "abs", "all", "any", "ascii", "bin", "bool", "bytearray", "bytes", "callable",
        "chr", "classmethod", "compile", "complex", "delattr", "dict", "dir", "divmod",
        "enumerate", "eval", "exec", "filter", "float", "format", "frozenset", "getattr",
        "globals", "hasattr", "hash", "help", "hex", "id", "input", "int", "isinstance",
        "issubclass", "iter", "len", "list", "locals", "map", "max", "memoryview", "min",
        "next", "object", "oct", "open", "ord", "pow", "print", "property", "range",
        "repr", "reversed", "round", "set", "setattr", "slice", "sorted", "staticmethod",
        "str", "sum", "super", "tuple", "type", "vars", "zip", "__import__", "self", "cls"
    )

    /**
     * Extracts variables defined and referenced in a Python code block.
     */
    fun extractVariables(source: String): Pair<Set<String>, Set<String>> {
        val defs = mutableSetOf<String>()
        val uses = mutableSetOf<String>()

        val lines = source.lines()
        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#") || line.isBlank()) continue

            // 1. Imports
            if (line.startsWith("import ")) {
                val importPart = line.removePrefix("import ").split("#")[0].trim()
                for (moduleToken in importPart.split(",")) {
                    val parts = moduleToken.trim().split(Regex("""\s+as\s+"""))
                    val alias = parts.last().trim().split(".")[0]
                    if (isValidIdentifier(alias)) defs.add(alias)
                }
                continue
            }
            if (line.startsWith("from ")) {
                val match = Regex("""from\s+[\w.]+\s+import\s+(.+)""").find(line)
                if (match != null) {
                    val importedNames = match.groupValues[1].split("#")[0]
                    for (token in importedNames.split(",")) {
                        val parts = token.trim().split(Regex("""\s+as\s+"""))
                        val alias = parts.last().trim()
                        if (isValidIdentifier(alias)) defs.add(alias)
                    }
                }
                continue
            }

            // 2. Function definitions
            if (line.startsWith("def ")) {
                val fnName = line.removePrefix("def ").takeWhile { it != '(' && it != ' ' }
                if (isValidIdentifier(fnName)) defs.add(fnName)
            }

            // 3. Class definitions
            if (line.startsWith("class ")) {
                val clsName = line.removePrefix("class ").takeWhile { it != '(' && it != ':' && it != ' ' }
                if (isValidIdentifier(clsName)) defs.add(clsName)
            }

            // 4. Assignments
            if (line.contains("=") && !line.contains("==") && !line.contains("!=") && !line.contains("<=") && !line.contains(">=")) {
                val left = line.substringBefore("=").trim()
                if (!left.startsWith("if ") && !left.startsWith("while ") && !left.startsWith("def ")) {
                    // Extract variable(s) on left hand side
                    val lhsTokens = Regex("""[a-zA-Z_][a-zA-Z0-9_]*""").findAll(left).map { it.value }
                    for (token in lhsTokens) {
                        if (isValidIdentifier(token)) {
                            defs.add(token)
                        }
                    }
                }
            }

            // 5. Scan all identifiers for uses
            val allTokens = Regex("""[a-zA-Z_][a-zA-Z0-9_]*""").findAll(line).map { it.value }
            for (token in allTokens) {
                if (isValidIdentifier(token) && token !in defs) {
                    uses.add(token)
                }
            }
        }

        return Pair(defs, uses)
    }

    private fun isValidIdentifier(token: String): Boolean {
        return token.isNotEmpty() &&
            token !in PYTHON_KEYWORDS &&
            token !in PYTHON_BUILTINS &&
            (token[0].isLetter() || token[0] == '_')
    }

    /**
     * Builds the complete dependency graph and checks for stale execution states across the notebook.
     */
    fun buildDependencyGraph(model: NotebookModel): NotebookDependencyGraph {
        val codeCells = model.cells.filter { it.cellType == CellType.CODE }
        val rawAnalyses = mutableMapOf<String, CellVariableAnalysis>()

        // 1. Extract defs and uses per cell
        codeCells.forEachIndexed { index, cell ->
            val (defs, uses) = extractVariables(cell.source)
            rawAnalyses[cell.id] = CellVariableAnalysis(
                cellId = cell.id,
                cellIndex = index,
                executionCount = cell.executionCount,
                definedVariables = defs,
                usedVariables = uses
            )
        }

        // 2. Build edges
        val edges = mutableListOf<DependencyEdge>()
        val upstreamMap = mutableMapOf<String, MutableSet<String>>()
        val downstreamMap = mutableMapOf<String, MutableSet<String>>()

        codeCells.forEach { cell ->
            upstreamMap[cell.id] = mutableSetOf()
            downstreamMap[cell.id] = mutableSetOf()
        }

        for (downstream in codeCells) {
            val downAnalysis = rawAnalyses[downstream.id] ?: continue
            for (usedVar in downAnalysis.usedVariables) {
                // Find nearest preceding upstream cell defining this variable
                val upstream = codeCells.takeWhile { it.id != downstream.id }
                    .lastOrNull { rawAnalyses[it.id]?.definedVariables?.contains(usedVar) == true }
                if (upstream != null) {
                    edges.add(DependencyEdge(upstream.id, downstream.id, usedVar))
                    upstreamMap[downstream.id]?.add(upstream.id)
                    downstreamMap[upstream.id]?.add(downstream.id)
                }
            }
        }

        // 3. Stale output detection:
        // A cell is stale if:
        // - It has executed (executionCount != null)
        // - Any upstream cell executed after it (or has higher executionCount / null executionCount while source modified)
        val staleIds = mutableSetOf<String>()
        for (cell in codeCells) {
            val currExec = cell.executionCount
            if (currExec != null) {
                val upstreams = upstreamMap[cell.id].orEmpty()
                for (upId in upstreams) {
                    val upCell = codeCells.find { it.id == upId }
                    if (upCell != null) {
                        val upExec = upCell.executionCount
                        // If upstream executed later (higher execution count) or was never re-run after modification
                        if (upExec == null || upExec > currExec) {
                            staleIds.add(cell.id)
                            break
                        }
                    }
                }
            }
        }

        // 4. Update analyses with mapped upstreams, downstreams, and stale flags
        val finalAnalyses = mutableMapOf<String, CellVariableAnalysis>()
        for (cell in codeCells) {
            val raw = rawAnalyses[cell.id] ?: continue
            finalAnalyses[cell.id] = raw.copy(
                upstreamCellIds = upstreamMap[cell.id].orEmpty(),
                downstreamCellIds = downstreamMap[cell.id].orEmpty(),
                isStale = cell.id in staleIds
            )
        }

        // 5. Compute topological order (Kahn's algorithm)
        val inDegree = mutableMapOf<String, Int>()
        codeCells.forEach { inDegree[it.id] = upstreamMap[it.id]?.size ?: 0 }

        val queue = ArrayDeque<String>()
        codeCells.forEach { if (inDegree[it.id] == 0) queue.add(it.id) }

        val topoOrder = mutableListOf<String>()
        while (queue.isNotEmpty()) {
            val u = queue.removeFirst()
            topoOrder.add(u)
            for (v in downstreamMap[u].orEmpty()) {
                val deg = (inDegree[v] ?: 1) - 1
                inDegree[v] = deg
                if (deg == 0) {
                    queue.add(v)
                }
            }
        }

        val hasCycles = topoOrder.size < codeCells.size

        return NotebookDependencyGraph(
            cellAnalyses = finalAnalyses,
            edges = edges,
            topologicalOrder = if (hasCycles) codeCells.map { it.id } else topoOrder,
            hasCycles = hasCycles,
            staleCellIds = staleIds
        )
    }
}
