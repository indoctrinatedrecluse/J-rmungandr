package org.jormungandr.dataframe.lakehouse

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.*
import javax.swing.table.DefaultTableModel

/**
 * Interactive Modern Lakehouse & Deep Parquet Inspector Studio.
 * Visualizes Parquet compression ratios, row group chunk distributions, dictionary encodings,
 * and enables Delta Lake / Apache Iceberg Time-Travel exploration.
 */
class LakehouseStudioPanel(
    private val project: Project,
    private val targetFile: File
) : JPanel(BorderLayout(0, 10)) {

    private val parquetReport = ParquetMetadataInspector.inspect(targetFile)
    private val lakehouseReport = LakehouseCatalogInspector.inspect(targetFile)

    init {
        border = JBUI.Borders.empty(12)
        buildUi()
    }

    private fun buildUi() {
        // Header
        val headerPanel = JPanel(BorderLayout(0, 4)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(10, 12)
            )
            val title = JBLabel("🧊 Modern Lakehouse & Deep Parquet Inspector").apply {
                font = font.deriveFont(Font.BOLD, 15f)
            }
            val subtitle = JBLabel("Target: ${targetFile.name} • Format: ${lakehouseReport.tableFormat} • Path: ${targetFile.absolutePath}").apply {
                foreground = JBColor.GRAY
                font = font.deriveFont(Font.PLAIN, 11f)
            }
            val titleRow = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(title, BorderLayout.WEST)
                val lic = org.jormungandr.core.license.LicenseService.getInstance().currentLicense.value
                val licLabel = JBLabel(" [${lic.licenseType.name}] ").apply {
                    font = font.deriveFont(Font.BOLD, 10.5f)
                    foreground = when (lic.licenseType) {
                        org.jormungandr.core.license.LicenseType.ADMIN -> java.awt.Color(245, 158, 11)
                        org.jormungandr.core.license.LicenseType.DEVELOPER -> java.awt.Color(168, 85, 247)
                        org.jormungandr.core.license.LicenseType.USER -> java.awt.Color(16, 185, 129)
                        org.jormungandr.core.license.LicenseType.TRIAL -> java.awt.Color(100, 116, 139)
                    }
                }
                add(licLabel, BorderLayout.EAST)
            }
            add(titleRow, BorderLayout.NORTH)
            add(subtitle, BorderLayout.SOUTH)
        }

        val tabs = JBTabbedPane()
        tabs.addTab("📦 Parquet Chunks & Compression", createParquetView())
        tabs.addTab("⏳ Time-Travel & ACID Log", createTimeTravelView())

        add(headerPanel, BorderLayout.NORTH)
        add(tabs, BorderLayout.CENTER)
    }

    private fun createParquetView(): JPanel {
        val panel = JPanel(BorderLayout(0, 10))

        // Metrics Banner
        val kpiPanel = JPanel(GridLayout(1, 5, 8, 0)).apply {
            border = JBUI.Borders.empty(6, 0)
            add(createKpiCard("File Size", formatBytes(parquetReport.fileSizeBytes)))
            add(createKpiCard("Total Rows", String.format("%,d", parquetReport.totalRows)))
            add(createKpiCard("Row Groups", "${parquetReport.rowGroupCount} groups"))
            add(createKpiCard("Codec", parquetReport.primaryCodec))
            add(createKpiCard("Compression", String.format("%.2fx ratio", parquetReport.overallCompressionRatio)))
        }

        // Columns metadata table
        val colNames = arrayOf("Column", "Physical Type", "Logical Type", "Codec", "Uncompressed", "Compressed", "Ratio", "Dict Encoded", "Bloom Filter", "Min Stat", "Max Stat")
        val colData = parquetReport.schemaColumns.map { c ->
            arrayOf(
                c.name,
                c.physicalType,
                c.logicalType,
                c.compressionCodec,
                formatBytes(c.uncompressedBytes),
                formatBytes(c.compressedBytes),
                String.format("%.2fx", c.compressionRatio),
                if (c.isDictionaryEncoded) "✅ Yes" else "❌ No",
                if (c.hasBloomFilter) "✅ Yes" else "❌ No",
                c.minStat,
                c.maxStat
            )
        }.toTypedArray()

        val colModel = DefaultTableModel(colData, colNames)
        val colTable = JBTable(colModel).apply {
            autoResizeMode = JTable.AUTO_RESIZE_ALL_COLUMNS
        }

        val tableCard = JPanel(BorderLayout(0, 6)).apply {
            val title = JBLabel("📊 Schema, Encodings & Column Chunk Statistics:").apply {
                font = font.deriveFont(Font.BOLD, 12f)
            }
            add(title, BorderLayout.NORTH)
            add(JBScrollPane(colTable), BorderLayout.CENTER)
        }

        panel.add(kpiPanel, BorderLayout.NORTH)
        panel.add(tableCard, BorderLayout.CENTER)
        return panel
    }

    private fun createTimeTravelView(): JPanel {
        val panel = JPanel(BorderLayout(0, 10))

        // Lakehouse Banner
        val kpiPanel = JPanel(GridLayout(1, 4, 8, 0)).apply {
            border = JBUI.Borders.empty(6, 0)
            add(createKpiCard("Format", lakehouseReport.tableFormat))
            add(createKpiCard("Current Version", "v${lakehouseReport.currentVersion}"))
            add(createKpiCard("Active Records", String.format("%,d", lakehouseReport.activeRecordCount)))
            add(createKpiCard("Partitions", lakehouseReport.partitionColumns.joinToString(", ")))
        }

        // History Commits table
        val commitNames = arrayOf("Version", "Timestamp", "Operation", "+ Files", "- Files", "Active Records", "Engine")
        val commitData = lakehouseReport.historyCommits.map { c ->
            arrayOf(
                "v${c.version}",
                c.formattedTime,
                c.operation,
                "+${c.addedFilesCount}",
                "-${c.removedFilesCount}",
                String.format("%,d", c.totalRecords),
                c.engine
            )
        }.toTypedArray()

        val commitModel = DefaultTableModel(commitData, commitNames)
        val commitTable = JBTable(commitModel)

        // Query preview & Action
        val sqlArea = JBTextArea(3, 40).apply {
            isEditable = false
            font = Font(Font.MONOSPACED, Font.PLAIN, 12)
            text = lakehouseReport.historyCommits.firstOrNull()?.timeTravelSql ?: "-- No Time-Travel SQL available"
        }

        commitTable.selectionModel.addListSelectionListener {
            val row = commitTable.selectedRow
            if (row >= 0 && row < lakehouseReport.historyCommits.size) {
                sqlArea.text = lakehouseReport.historyCommits[row].timeTravelSql
            }
        }

        val queryCard = JPanel(BorderLayout(0, 6)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(8, 10)
            )
            val qHeader = JPanel(BorderLayout()).apply {
                val lbl = JBLabel("⚡ Time-Travel SQL Query (DuckDB / Lakehouse Dialect):").apply {
                    font = font.deriveFont(Font.BOLD, 12f)
                }
                val copyBtn = JButton("📋 Copy Query").apply {
                    addActionListener {
                        if (!org.jormungandr.core.license.LicenseService.getInstance().isLicensed()) {
                            JOptionPane.showMessageDialog(
                                this@LakehouseStudioPanel,
                                "Lakehouse Time-Travel & ACID Log Export is locked in Trial Mode.\nA valid User, Developer, or Admin commercial license is required.",
                                "Commercial License Required",
                                JOptionPane.WARNING_MESSAGE
                            )
                            return@addActionListener
                        }
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(sqlArea.text), null)
                        JOptionPane.showMessageDialog(this@LakehouseStudioPanel, "Time-Travel SQL copied to clipboard!", "Copied", JOptionPane.INFORMATION_MESSAGE)
                    }
                }
                add(lbl, BorderLayout.WEST)
                add(copyBtn, BorderLayout.EAST)
            }
            add(qHeader, BorderLayout.NORTH)
            add(JBScrollPane(sqlArea), BorderLayout.CENTER)
        }

        val tableCard = JPanel(BorderLayout(0, 6)).apply {
            val title = JBLabel("📜 ACID Commit History & Transaction Log:").apply {
                font = font.deriveFont(Font.BOLD, 12f)
            }
            add(title, BorderLayout.NORTH)
            add(JBScrollPane(commitTable), BorderLayout.CENTER)
        }

        panel.add(kpiPanel, BorderLayout.NORTH)
        panel.add(tableCard, BorderLayout.CENTER)
        panel.add(queryCard, BorderLayout.SOUTH)
        return panel
    }

    private fun createKpiCard(label: String, value: String): JPanel {
        return JPanel(BorderLayout(0, 2)).apply {
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(JBColor.border(), 1, true),
                JBUI.Borders.empty(8, 10)
            )
            val l = JBLabel(label).apply {
                font = font.deriveFont(Font.BOLD, 10f)
                foreground = JBColor.GRAY
            }
            val v = JBLabel(value).apply {
                font = font.deriveFont(Font.BOLD, 13f)
            }
            add(l, BorderLayout.NORTH)
            add(v, BorderLayout.SOUTH)
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes >= 1024 * 1024 * 1024 -> String.format("%.2f GB", bytes.toDouble() / (1024 * 1024 * 1024))
            bytes >= 1024 * 1024 -> String.format("%.2f MB", bytes.toDouble() / (1024 * 1024))
            bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
            else -> "$bytes B"
        }
    }
}
