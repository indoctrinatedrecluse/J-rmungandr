package org.jormungandr.jupyter.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookModelTest {

    @Test
    fun `test default notebook creation`() {
        val model = NotebookModel.createDefaultPythonNotebook()
        assertEquals(2, model.cells.size)
        assertEquals(CellType.MARKDOWN, model.cells[0].cellType)
        assertEquals(CellType.CODE, model.cells[1].cellType)
        assertTrue(model.cells[0].source.contains("Jörmungandr"))
    }

    @Test
    fun `test adding and moving cells`() {
        val model = NotebookModel()
        val c1 = model.addCell(CellType.CODE, "print(1)")
        val c2 = model.addCell(CellType.CODE, "print(2)")
        val c3 = model.addCell(CellType.MARKDOWN, "# Title")

        assertEquals(3, model.cells.size)
        assertEquals(c1.id, model.cells[0].id)
        assertEquals(c2.id, model.cells[1].id)
        assertEquals(c3.id, model.cells[2].id)

        // Move c3 from index 2 to index 0
        assertTrue(model.moveCell(2, 0))
        assertEquals(c3.id, model.cells[0].id)
        assertEquals(c1.id, model.cells[1].id)
        assertEquals(c2.id, model.cells[2].id)

        // Remove c1
        assertTrue(model.removeCell(c1.id))
        assertEquals(2, model.cells.size)
        assertEquals(c3.id, model.cells[0].id)
        assertEquals(c2.id, model.cells[1].id)
    }

    @Test
    fun `test threaded comments on cells`() {
        val cell = NotebookCell(cellType = CellType.CODE, source = "a = 42")
        val comment = cell.addComment(author = "alice", text = "Consider renaming variable 'a' for clarity.")

        assertEquals(1, cell.comments.size)
        assertEquals("alice", comment.author)
        assertEquals("Consider renaming variable 'a' for clarity.", comment.text)
        assertFalse(comment.resolved)

        // Add reply
        comment.replies.add(CellCommentReply(author = "bob", text = "Good point, renamed to answer_to_everything."))
        assertEquals(1, comment.replies.size)
        assertEquals("bob", comment.replies[0].author)

        // Resolve comment
        comment.resolved = true
        assertTrue(cell.comments[0].resolved)
    }

    @Test
    fun `test clear outputs resets execution count and results`() {
        val cell = NotebookCell(
            cellType = CellType.CODE,
            source = "x = 1",
            executionCount = 5,
            outputs = mutableListOf(
                CellOutput.StreamOutput("stdout", "done\n"),
                CellOutput.ExecuteResultOutput(5, mapOf("text/plain" to "1"))
            )
        )

        assertEquals(2, cell.outputs.size)
        assertEquals(5, cell.executionCount)

        cell.clearOutputs()
        assertTrue(cell.outputs.isEmpty())
        assertNull(cell.executionCount)
    }
}
