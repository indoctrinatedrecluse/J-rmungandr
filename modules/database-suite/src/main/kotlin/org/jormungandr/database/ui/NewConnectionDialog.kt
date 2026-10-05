package org.jormungandr.database.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import org.jormungandr.database.engine.DatabaseConnectionManager
import org.jormungandr.database.model.ConnectionConfig
import org.jormungandr.database.model.DatabaseDialect
import java.awt.BorderLayout
import java.awt.Color
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.util.UUID
import javax.swing.*
import javax.swing.border.EmptyBorder

class NewConnectionDialog(project: Project? = null) : DialogWrapper(project, true) {

    private val nameField = JBTextField("Sample SQLite", 20)
    private val dialectCombo = JComboBox(DatabaseDialect.values())
    private val hostField = JBTextField("localhost", 20)
    private val portField = JBTextField("0", 8)
    private val dbNameField = JBTextField(":memory:", 20)
    private val userField = JBTextField("", 20)
    private val passField = JBPasswordField().apply { columns = 20 }
    private val testStatusLabel = JBLabel("")

    var resultConfig: ConnectionConfig? = null
        private set

    init {
        title = "New Database Connection"
        dialectCombo.selectedItem = DatabaseDialect.SQLITE
        dialectCombo.addActionListener {
            val d = dialectCombo.selectedItem as DatabaseDialect
            portField.text = d.defaultPort.toString()
            if (d == DatabaseDialect.SQLITE) {
                dbNameField.text = ":memory:"
            } else if (d == DatabaseDialect.DUCKDB) {
                dbNameField.text = ":memory:"
            } else {
                dbNameField.text = "postgres"
            }
        }
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(12, 12)).apply {
            border = EmptyBorder(12, 16, 12, 16)
        }

        val form = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            insets = Insets(4, 4, 4, 4)
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
        }

        fun addRow(label: String, comp: JComponent, row: Int) {
            gbc.gridx = 0; gbc.gridy = row; gbc.weightx = 0.0
            form.add(JBLabel(label), gbc)
            gbc.gridx = 1; gbc.gridy = row; gbc.weightx = 1.0
            form.add(comp, gbc)
        }

        addRow("Connection Name:", nameField, 0)
        addRow("Dialect / Engine:", dialectCombo, 1)
        addRow("Host:", hostField, 2)
        addRow("Port:", portField, 3)
        addRow("Database / File:", dbNameField, 4)
        addRow("Username:", userField, 5)
        addRow("Password:", passField, 6)

        val bottomBar = JPanel(BorderLayout()).apply {
            border = EmptyBorder(8, 0, 0, 0)
        }
        val testBtn = JButton("Test Connection").apply {
            addActionListener {
                val cfg = buildCurrentConfig()
                val testRes = DatabaseConnectionManager.testConnection(cfg)
                if (testRes.isSuccess && testRes.getOrNull() == true) {
                    testStatusLabel.text = "✓ Connection Successful"
                    testStatusLabel.foreground = Color(40, 160, 60)
                } else {
                    val err = testRes.exceptionOrNull()?.message ?: "Failed to connect"
                    testStatusLabel.text = "✗ $err"
                    testStatusLabel.foreground = Color(200, 40, 40)
                }
            }
        }
        bottomBar.add(testBtn, BorderLayout.WEST)
        bottomBar.add(testStatusLabel, BorderLayout.CENTER)

        root.add(form, BorderLayout.CENTER)
        root.add(bottomBar, BorderLayout.SOUTH)
        return root
    }

    private fun buildCurrentConfig(): ConnectionConfig {
        val d = dialectCombo.selectedItem as DatabaseDialect
        return ConnectionConfig(
            id = UUID.randomUUID().toString(),
            name = nameField.text.ifBlank { "Database_${d.name}" },
            dialect = d,
            host = hostField.text.trim(),
            port = portField.text.trim().toIntOrNull() ?: d.defaultPort,
            databaseName = dbNameField.text.trim(),
            username = userField.text.trim(),
            password = String(passField.password)
        )
    }

    override fun doOKAction() {
        resultConfig = buildCurrentConfig()
        super.doOKAction()
    }
}
