package org.jormungandr.jupyter.format

import org.jormungandr.jupyter.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookFormatTest {

    @Test
    fun `test reading standard json notebook`() {
        val json = """
        {
          "nbformat": 4,
          "nbformat_minor": 5,
          "metadata": {
            "kernelspec": {
              "name": "python3",
              "display_name": "Python 3",
              "language": "python"
            }
          },
          "cells": [
            {
              "id": "c1",
              "cell_type": "markdown",
              "source": [
                "# Hello World\n",
                "This is a test notebook."
              ],
              "metadata": {}
            },
            {
              "id": "c2",
              "cell_type": "code",
              "source": [
                "print('Hello from Jörmungandr!')\n"
              ],
              "execution_count": 1,
              "metadata": {},
              "outputs": [
                {
                  "output_type": "stream",
                  "name": "stdout",
                  "text": [
                    "Hello from Jörmungandr!\n"
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val model = NotebookFormat.readNotebook(json)
        assertEquals(2, model.cells.size)
        assertEquals(CellType.MARKDOWN, model.cells[0].cellType)
        assertTrue(model.cells[0].source.contains("# Hello World"))

        assertEquals(CellType.CODE, model.cells[1].cellType)
        assertEquals(1, model.cells[1].executionCount)
        assertEquals(1, model.cells[1].outputs.size)

        val out = model.cells[1].outputs[0] as CellOutput.StreamOutput
        assertEquals("stdout", out.name)
        assertTrue(out.text.contains("Hello from Jörmungandr!"))
    }

    @Test
    fun `test round-trip serialization with threaded comments`() {
        val model = NotebookModel()
        val cell = model.addCell(CellType.CODE, "def add(a, b):\n    return a + b\n")
        cell.executionCount = 2
        cell.outputs.add(CellOutput.StreamOutput("stdout", "Loaded function\n"))

        val comment = cell.addComment("reviewer", "Needs type annotations.")
        comment.replies.add(CellCommentReply(author = "author", text = "Will add int hints."))

        val json = NotebookFormat.writeNotebook(model)
        assertTrue(json.contains("Needs type annotations."))
        assertTrue(json.contains("Will add int hints."))

        // Read it back
        val loaded = NotebookFormat.readNotebook(json)
        assertEquals(1, loaded.cells.size)
        val loadedCell = loaded.cells[0]
        assertEquals(1, loadedCell.comments.size)
        assertEquals("reviewer", loadedCell.comments[0].author)
        assertEquals("Needs type annotations.", loadedCell.comments[0].text)
        assertEquals(1, loadedCell.comments[0].replies.size)
        assertEquals("author", loadedCell.comments[0].replies[0].author)
        assertEquals("Will add int hints.", loadedCell.comments[0].replies[0].text)
    }

    @Test
    fun `test reading and writing rich mime outputs`() {
        val model = NotebookModel()
        val cell = model.addCell(CellType.CODE, "plt.plot([1, 2, 3])")
        cell.outputs.add(
            CellOutput.DisplayDataOutput(
                data = mapOf(
                    "text/plain" to "<Figure size 640x480 with 1 Axes>",
                    "image/png" to "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
                )
            )
        )

        val json = NotebookFormat.writeNotebook(model)
        val loaded = NotebookFormat.readNotebook(json)

        val out = loaded.cells[0].outputs[0] as CellOutput.DisplayDataOutput
        assertEquals("<Figure size 640x480 with 1 Axes>", out.getPlainText())
        assertNotNull(out.getPngBase64())
    }
}
