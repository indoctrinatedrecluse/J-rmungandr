package org.jormungandr.jupyter.diff

import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookDiffServiceTest {

    @Test
    fun `test identical notebooks produce unchanged status`() {
        val model1 = NotebookModel()
        model1.addCell(CellType.CODE, "print('hello')")
        val model2 = NotebookModel()
        model2.addCell(CellType.CODE, "print('hello')")

        val res = NotebookDiffService.diff(model1, model2)
        assertEquals(0, res.addedCount)
        assertEquals(0, res.deletedCount)
        assertEquals(0, res.modifiedCount)
        assertEquals(1, res.unchangedCount)
        assertEquals(DiffStatus.UNCHANGED, res.items[0].status)
    }

    @Test
    fun `test detecting added and modified cells`() {
        val model1 = NotebookModel()
        model1.addCell(CellType.CODE, "x = 10")

        val model2 = NotebookModel()
        model2.addCell(CellType.CODE, "x = 20\nprint(x)")
        model2.addCell(CellType.MARKDOWN, "# New Section")

        val res = NotebookDiffService.diff(model1, model2)
        assertEquals(1, res.modifiedCount)
        assertEquals(1, res.addedCount)
        assertEquals(DiffStatus.MODIFIED, res.items[0].status)
        assertEquals(DiffStatus.ADDED, res.items[1].status)
    }

    @Test
    fun `test detecting deleted cell`() {
        val model1 = NotebookModel()
        model1.addCell(CellType.CODE, "x = 1")
        model1.addCell(CellType.CODE, "y = 2")

        val model2 = NotebookModel()
        model2.addCell(CellType.CODE, "x = 1")

        val res = NotebookDiffService.diff(model1, model2)
        assertEquals(1, res.unchangedCount)
        assertEquals(1, res.deletedCount)
        assertEquals(DiffStatus.DELETED, res.items[1].status)
    }
}
