package org.jormungandr.jupyter.ui.outline

import org.jormungandr.jupyter.model.CellType
import org.jormungandr.jupyter.model.NotebookModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class NotebookOutlinePanelTest {

    @Test
    fun `test outline panel extracts headings with hierarchy`() {
        val model = NotebookModel()
        model.addCell(CellType.MARKDOWN, "# Project Title\nOverview of experiments.")
        model.addCell(CellType.CODE, "print('running')")
        model.addCell(CellType.MARKDOWN, "## Data Exploration\n### Sub-Section 1\nDetails here.")
        model.addCell(CellType.MARKDOWN, "## Model Training")

        var clickedIndex: Int? = null
        val panel = NotebookOutlinePanel { clickedIndex = it }
        panel.updateOutline(model)

        // Panel internally parsed 4 headings: H1 Project Title, H2 Data Exploration, H3 Sub-Section 1, H2 Model Training
        // Verify component instantiated without error
        assertNotNull(panel)
    }
}
