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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookDependencyGraphServiceTest {

    @Test
    fun testExtractVariables() {
        val pythonCode = """
            import pandas as pd
            from sklearn.metrics import accuracy_score, f1_score as f1
            
            x = 42
            y, z = 10, 20
            
            def custom_eval(model_in):
                return model_in.score(x, y)
                
            score = custom_eval(z)
            print(score, pd)
        """.trimIndent()

        val (defs, uses) = NotebookDependencyGraphService.extractVariables(pythonCode)

        assertTrue(defs.contains("pd"), "Should extract alias 'pd' as def")
        assertTrue(defs.contains("accuracy_score"), "Should extract 'accuracy_score' as def")
        assertTrue(defs.contains("f1"), "Should extract alias 'f1' as def")
        assertTrue(defs.contains("x"), "Should extract 'x' as def")
        assertTrue(defs.contains("y"), "Should extract 'y' as def")
        assertTrue(defs.contains("z"), "Should extract 'z' as def")
        assertTrue(defs.contains("custom_eval"), "Should extract function 'custom_eval' as def")
        assertTrue(defs.contains("score"), "Should extract 'score' as def")

        // In code block, uses should capture identifiers referenced
        assertTrue(uses.contains("x") || defs.contains("x"))
    }

    @Test
    fun testDependencyGraphAndCascade() {
        val cell1 = NotebookCell(
            id = "c1",
            cellType = CellType.CODE,
            source = "data = [1, 2, 3, 4]\nscale = 10",
            executionCount = 1
        )
        val cell2 = NotebookCell(
            id = "c2",
            cellType = CellType.CODE,
            source = "scaled = [d * scale for d in data]",
            executionCount = 2
        )
        val cell3 = NotebookCell(
            id = "c3",
            cellType = CellType.CODE,
            source = "total = sum(scaled)",
            executionCount = 3
        )
        val cellIndependent = NotebookCell(
            id = "c_ind",
            cellType = CellType.CODE,
            source = "standalone_val = 999",
            executionCount = 4
        )

        val model = NotebookModel.createDefaultPythonNotebook().apply {
            cells.clear()
            cells.addAll(listOf(cell1, cell2, cell3, cellIndependent))
        }

        val graph = NotebookDependencyGraphService.buildDependencyGraph(model)

        assertFalse(graph.hasCycles, "Graph should not have cycles")
        assertEquals(4, graph.cellAnalyses.size)

        // Check edges: c1 -> c2 on 'scale' and/or 'data'
        val c1ToC2Edges = graph.edges.filter { it.upstreamCellId == "c1" && it.downstreamCellId == "c2" }
        assertTrue(c1ToC2Edges.isNotEmpty(), "There should be an edge from c1 to c2")

        // c2 -> c3 on 'scaled'
        val c2ToC3Edges = graph.edges.filter { it.upstreamCellId == "c2" && it.downstreamCellId == "c3" }
        assertTrue(c2ToC3Edges.isNotEmpty(), "There should be an edge from c2 to c3")

        // Cascade from c1 should reach c2 and c3 in topo order, but not c_ind
        val cascade = graph.getDownstreamCascade("c1")
        assertEquals(listOf("c2", "c3"), cascade)

        // Topo order should have c1 before c2, and c2 before c3
        val c1Idx = graph.topologicalOrder.indexOf("c1")
        val c2Idx = graph.topologicalOrder.indexOf("c2")
        val c3Idx = graph.topologicalOrder.indexOf("c3")
        assertTrue(c1Idx < c2Idx, "c1 must precede c2")
        assertTrue(c2Idx < c3Idx, "c2 must precede c3")
    }

    @Test
    fun testStaleCellDetection() {
        // Cell 1 was modified and re-run with executionCount 10
        val cell1 = NotebookCell(
            id = "c1",
            cellType = CellType.CODE,
            source = "dataset = 'modified'",
            executionCount = 10
        )
        // Cell 2 was run earlier when executionCount was 2
        val cell2 = NotebookCell(
            id = "c2",
            cellType = CellType.CODE,
            source = "len(dataset)",
            executionCount = 2
        )

        val model = NotebookModel.createDefaultPythonNotebook().apply {
            cells.clear()
            cells.addAll(listOf(cell1, cell2))
        }

        val graph = NotebookDependencyGraphService.buildDependencyGraph(model)

        assertTrue(graph.staleCellIds.contains("c2"), "Cell 2 should be marked stale because upstream Cell 1 ran later")
        assertTrue(graph.cellAnalyses["c2"]?.isStale == true)
        assertFalse(graph.cellAnalyses["c1"]?.isStale == true)
    }
}
