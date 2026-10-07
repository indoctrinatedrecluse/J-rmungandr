package org.jormungandr.jupyter.diff

import org.jormungandr.jupyter.model.NotebookCell
import org.jormungandr.jupyter.model.NotebookModel

/**
 * Status of cell comparison in a notebook diff.
 */
enum class DiffStatus {
    UNCHANGED,
    ADDED,
    DELETED,
    MODIFIED
}

/**
 * Detailed difference comparison for an individual notebook cell.
 */
data class CellDiffItem(
    val index: Int,
    val status: DiffStatus,
    val oldCell: NotebookCell?,
    val newCell: NotebookCell?,
    val summary: String,
    val sourceDiff: List<String>
)

/**
 * Aggregated summary of comparison between two notebooks.
 */
data class NotebookDiffResult(
    val items: List<CellDiffItem>,
    val addedCount: Int,
    val deletedCount: Int,
    val modifiedCount: Int,
    val unchangedCount: Int
)

/**
 * Visual notebook difference engine providing cell-by-cell semantic comparison
 * for Jupyter Notebooks (.ipynb) instead of unreadable raw JSON git diffs.
 */
object NotebookDiffService {

    /**
     * Compares [oldModel] against [newModel] cell-by-cell.
     */
    fun diff(oldModel: NotebookModel, newModel: NotebookModel): NotebookDiffResult {
        val items = mutableListOf<CellDiffItem>()
        val maxLen = maxOf(oldModel.cells.size, newModel.cells.size)
        var added = 0
        var deleted = 0
        var modified = 0
        var unchanged = 0

        for (i in 0 until maxLen) {
            val oldCell = oldModel.cells.getOrNull(i)
            val newCell = newModel.cells.getOrNull(i)

            when {
                oldCell == null && newCell != null -> {
                    added++
                    items.add(
                        CellDiffItem(
                            index = i,
                            status = DiffStatus.ADDED,
                            oldCell = null,
                            newCell = newCell,
                            summary = "Cell added (${newCell.cellType.value})",
                            sourceDiff = newCell.source.lines().map { "+ $it" }
                        )
                    )
                }

                oldCell != null && newCell == null -> {
                    deleted++
                    items.add(
                        CellDiffItem(
                            index = i,
                            status = DiffStatus.DELETED,
                            oldCell = oldCell,
                            newCell = null,
                            summary = "Cell removed (${oldCell.cellType.value})",
                            sourceDiff = oldCell.source.lines().map { "- $it" }
                        )
                    )
                }

                oldCell != null && newCell != null -> {
                    val typeChanged = oldCell.cellType != newCell.cellType
                    val sourceChanged = oldCell.source != newCell.source
                    val outputsChanged = oldCell.outputs.size != newCell.outputs.size

                    if (!typeChanged && !sourceChanged && !outputsChanged) {
                        unchanged++
                        items.add(
                            CellDiffItem(
                                index = i,
                                status = DiffStatus.UNCHANGED,
                                oldCell = oldCell,
                                newCell = newCell,
                                summary = "Unchanged",
                                sourceDiff = newCell.source.lines().map { "  $it" }
                            )
                        )
                    } else {
                        modified++
                        val diffLines = computeLineDiff(oldCell.source, newCell.source)
                        val summaryText = buildString {
                            if (typeChanged) append("Type changed (${oldCell.cellType.value} -> ${newCell.cellType.value}); ")
                            if (sourceChanged) append("Source modified; ")
                            if (outputsChanged) append("Outputs updated")
                        }.trim().removeSuffix(";")

                        items.add(
                            CellDiffItem(
                                index = i,
                                status = DiffStatus.MODIFIED,
                                oldCell = oldCell,
                                newCell = newCell,
                                summary = summaryText,
                                sourceDiff = diffLines
                            )
                        )
                    }
                }
            }
        }

        return NotebookDiffResult(
            items = items,
            addedCount = added,
            deletedCount = deleted,
            modifiedCount = modified,
            unchangedCount = unchanged
        )
    }

    private fun computeLineDiff(oldText: String, newText: String): List<String> {
        val oldLines = oldText.lines()
        val newLines = newText.lines()
        val res = mutableListOf<String>()

        val maxLines = maxOf(oldLines.size, newLines.size)
        for (j in 0 until maxLines) {
            val o = oldLines.getOrNull(j)
            val n = newLines.getOrNull(j)
            if (o == n) {
                if (n != null) res.add("  $n")
            } else {
                if (o != null) res.add("- $o")
                if (n != null) res.add("+ $n")
            }
        }
        return res
    }
}
