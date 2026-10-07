package org.jormungandr.shell.project

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import org.jormungandr.shell.icon.JormungandrIcons
import java.awt.*
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Interactive project creation dialog for data science, analytics, and machine learning workspaces.
 */
class NewDataScienceProjectDialog(
    private val currentProject: Project? = null,
    defaultParentDir: File? = null
) : DialogWrapper(currentProject, true) {

    private val templates = DataScienceProjectTemplate.entries.toTypedArray()
    private val templateListModel = DefaultListModel<DataScienceProjectTemplate>()
    private val templateList: JBList<DataScienceProjectTemplate>

    // Form inputs
    private val projectNameField = JBTextField("my_data_science_project")
    private val locationField = JBTextField()
    private val browseButton = JButton("Browse...")
    private val envComboBox = JComboBox(EnvironmentType.entries.toTypedArray())

    // Option checkboxes
    private val initGitCheckBox = JCheckBox("Initialize Git repository (.git)", true)
    private val autoVenvCheckBox = JCheckBox("Auto-create virtual environment (.venv) if tool is available", false)
    private val openPrimaryCheckBox = JCheckBox("Open primary notebook / starter file upon creation", true)

    // Preview components
    private val titleLabel = JLabel()
    private val categoryLabel = JLabel()
    private val descLabel = JLabel()
    private val librariesPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
    private val previewFilesListModel = DefaultListModel<String>()
    private val previewFilesList = JBList(previewFilesListModel)

    var scaffoldResult: ScaffoldResult? = null
        private set

    init {
        title = "New Data Science Workspace"
        isResizable = true

        val baseDir = defaultParentDir ?: currentProject?.basePath?.let { File(it).parentFile }
            ?: File(System.getProperty("user.home"), "DataProjects")
        locationField.text = File(baseDir, projectNameField.text).absolutePath

        for (template in templates) {
            templateListModel.addElement(template)
        }

        templateList = JBList(templateListModel).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = TemplateListCellRenderer()
            fixedCellHeight = 62
            addListSelectionListener { updateDetails(selectedValue) }
        }

        init()

        if (!templateListModel.isEmpty) {
            templateList.selectedIndex = 0
        }

        setupListeners(baseDir)
    }

    private fun setupListeners(baseParentDir: File) {
        val docListener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updatePath()
            override fun removeUpdate(e: DocumentEvent?) = updatePath()
            override fun changedUpdate(e: DocumentEvent?) = updatePath()

            private fun updatePath() {
                val name = projectNameField.text.trim().ifEmpty { "untitled_project" }
                val currentParent = File(locationField.text).parentFile ?: baseParentDir
                locationField.text = File(currentParent, name).absolutePath
                updatePreviewFiles(templateList.selectedValue)
            }
        }
        projectNameField.document.addDocumentListener(docListener)

        browseButton.addActionListener {
            val chooser = JFileChooser().apply {
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                dialogTitle = "Select Workspace Destination Folder"
                val curr = File(locationField.text).parentFile
                if (curr?.exists() == true) {
                    currentDirectory = curr
                }
            }
            val res = chooser.showOpenDialog(contentPane)
            if (res == JFileChooser.APPROVE_OPTION && chooser.selectedFile != null) {
                val chosenParent = chooser.selectedFile
                val name = projectNameField.text.trim().ifEmpty { "untitled_project" }
                locationField.text = File(chosenParent, name).absolutePath
            }
        }

        envComboBox.addActionListener {
            updatePreviewFiles(templateList.selectedValue)
        }
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(16, 12)).apply {
            preferredSize = Dimension(820, 560)
            border = EmptyBorder(12, 16, 12, 16)
        }

        // Header Banner
        val header = JPanel(BorderLayout(12, 0)).apply {
            isOpaque = false
            border = EmptyBorder(0, 0, 8, 0)
        }
        val iconLabel = JLabel(JormungandrIcons.LOGO_32)
        val titleText = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            val hLabel = JBLabel("Create Data Science & ML Workspace").apply {
                font = font.deriveFont(Font.BOLD, 16f)
            }
            val subLabel = JBLabel("Scaffold production-grade project layouts, virtual environments, and notebooks.").apply {
                font = font.deriveFont(Font.PLAIN, 11f)
                foreground = Color(110, 110, 110)
            }
            add(hLabel)
            add(Box.createVerticalStrut(2))
            add(subLabel)
        }
        header.add(iconLabel, BorderLayout.WEST)
        header.add(titleText, BorderLayout.CENTER)
        root.add(header, BorderLayout.NORTH)

        // Split Pane (Templates Left, Configuration & Preview Right)
        val leftScroll = JBScrollPane(templateList).apply {
            preferredSize = Dimension(300, 420)
            border = LineBorder(Color(215, 215, 215), 1)
        }

        val rightPanel = JPanel(BorderLayout(0, 12)).apply {
            border = EmptyBorder(0, 8, 0, 0)
        }

        // Form Inputs Panel
        val formPanel = JPanel(GridBagLayout()).apply {
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(220, 220, 220), 1, true),
                EmptyBorder(12, 14, 12, 14)
            )
            background = Color(250, 250, 250)
        }

        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
            gridx = 0
            gridy = 0
        }

        // Project Name
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Project Name:").apply { font = font.deriveFont(Font.BOLD, 12f) }, gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(projectNameField, gbc)

        // Location
        gbc.gridy = 1
        gbc.gridx = 0
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Location:").apply { font = font.deriveFont(Font.BOLD, 12f) }, gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        val locationPanel = JPanel(BorderLayout(6, 0)).apply {
            isOpaque = false
            add(locationField, BorderLayout.CENTER)
            add(browseButton, BorderLayout.EAST)
        }
        formPanel.add(locationPanel, gbc)

        // Environment Manager
        gbc.gridy = 2
        gbc.gridx = 0
        gbc.weightx = 0.0
        formPanel.add(JBLabel("Environment:").apply { font = font.deriveFont(Font.BOLD, 12f) }, gbc)
        gbc.gridx = 1
        gbc.weightx = 1.0
        formPanel.add(envComboBox, gbc)

        // Options Checkboxes
        gbc.gridy = 3
        gbc.gridx = 1
        val checkPanel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            add(initGitCheckBox)
            add(autoVenvCheckBox)
            add(openPrimaryCheckBox)
        }
        formPanel.add(checkPanel, gbc)

        rightPanel.add(formPanel, BorderLayout.NORTH)

        // Details & Files Preview Card
        val detailCard = JPanel(BorderLayout(0, 8)).apply {
            background = Color(253, 246, 227) // Solarized Base3 cream card
            border = BorderFactory.createCompoundBorder(
                LineBorder(Color(220, 215, 200), 1, true),
                EmptyBorder(12, 14, 12, 14)
            )
        }

        val detailHeader = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            isOpaque = false
            titleLabel.font = titleLabel.font.deriveFont(Font.BOLD, 14f)
            categoryLabel.font = categoryLabel.font.deriveFont(Font.BOLD, 10f)
            categoryLabel.foreground = Color(38, 139, 210)
            descLabel.font = descLabel.font.deriveFont(Font.PLAIN, 11f)
            descLabel.foreground = Color(88, 110, 117)
            librariesPanel.isOpaque = false

            add(titleLabel)
            add(Box.createVerticalStrut(2))
            add(categoryLabel)
            add(Box.createVerticalStrut(4))
            add(descLabel)
            add(Box.createVerticalStrut(6))
            add(librariesPanel)
        }
        detailCard.add(detailHeader, BorderLayout.NORTH)

        // Files preview list
        previewFilesList.apply {
            cellRenderer = DefaultListCellRenderer().apply {
                font = Font(Font.MONOSPACED, Font.PLAIN, 11)
            }
        }
        val previewScroll = JBScrollPane(previewFilesList).apply {
            border = BorderFactory.createTitledBorder(
                LineBorder(Color(200, 195, 180), 1),
                "Scaffolded File Structure"
            )
            preferredSize = Dimension(400, 140)
        }
        detailCard.add(previewScroll, BorderLayout.CENTER)

        rightPanel.add(detailCard, BorderLayout.CENTER)

        val splitPane = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightPanel).apply {
            dividerLocation = 300
            dividerSize = 6
            isContinuousLayout = true
            border = null
        }
        root.add(splitPane, BorderLayout.CENTER)

        return root
    }

    private fun updateDetails(template: DataScienceProjectTemplate?) {
        if (template == null) return

        titleLabel.text = "${template.iconEmoji} ${template.displayName}"
        categoryLabel.text = template.category.uppercase()
        descLabel.text = "<html>${template.description}</html>"

        librariesPanel.removeAll()
        for (lib in template.coreLibraries) {
            librariesPanel.add(createChip(lib, Color(238, 232, 213), Color(101, 123, 131)))
        }
        librariesPanel.revalidate()
        librariesPanel.repaint()

        updatePreviewFiles(template)
    }

    private fun updatePreviewFiles(template: DataScienceProjectTemplate?) {
        if (template == null) return
        val name = projectNameField.text.trim().ifEmpty { "untitled_project" }
        val env = envComboBox.selectedItem as? EnvironmentType ?: EnvironmentType.UV

        previewFilesListModel.clear()
        val templateFiles = template.generateFiles(name)
        for (tf in templateFiles) {
            val prefix = if (tf.isPrimaryToOpen) "★ " else "  "
            previewFilesListModel.addElement("$prefix${tf.relativePath}")
        }
        val envFiles = env.generateEnvironmentFiles(name, template.defaultDependencies)
        for (ef in envFiles) {
            previewFilesListModel.addElement("  ${ef.relativePath}")
        }
        if (initGitCheckBox.isSelected) {
            previewFilesListModel.addElement("  .git/ (Repository)")
        }
    }

    private fun createChip(text: String, bg: Color, fg: Color): JLabel {
        return JLabel(text).apply {
            font = font.deriveFont(Font.BOLD, 10f)
            isOpaque = true
            background = bg
            foreground = fg
            border = BorderFactory.createCompoundBorder(
                LineBorder(fg.brighter(), 1, true),
                EmptyBorder(2, 6, 2, 6)
            )
        }
    }

    override fun doOKAction() {
        val name = projectNameField.text.trim()
        if (name.isEmpty()) {
            Messages.showErrorDialog("Project name cannot be blank.", "Validation Error")
            return
        }
        if (!name.matches(Regex("^[a-zA-Z0-9_-]+$"))) {
            Messages.showErrorDialog("Project name may only contain alphanumeric characters, hyphens, and underscores.", "Validation Error")
            return
        }

        val targetDir = File(locationField.text.trim())
        if (targetDir.exists() && (targetDir.listFiles()?.isNotEmpty() == true)) {
            val confirm = Messages.showYesNoDialog(
                "Destination directory '${targetDir.absolutePath}' is not empty. Continue scaffolding into this folder?",
                "Directory Not Empty",
                Messages.getWarningIcon()
            )
            if (confirm != Messages.YES) {
                return
            }
        }

        val selectedTemplate = templateList.selectedValue ?: DataScienceProjectTemplate.MACHINE_LEARNING
        val selectedEnv = envComboBox.selectedItem as? EnvironmentType ?: EnvironmentType.UV

        val options = ScaffoldOptions(
            projectName = name,
            targetDirectory = targetDir,
            template = selectedTemplate,
            environmentType = selectedEnv,
            initGit = initGitCheckBox.isSelected,
            autoCreateVenv = autoVenvCheckBox.isSelected
        )

        val result = DataScienceProjectScaffolder.scaffold(options)
        this.scaffoldResult = result

        if (!result.success) {
            Messages.showErrorDialog("Scaffolding failed: ${result.message}", "Error Creating Project")
            return
        }

        super.doOKAction()
    }

    val isOpenPrimaryRequested: Boolean
        get() = openPrimaryCheckBox.isSelected

    /** Custom list cell renderer for template items */
    private inner class TemplateListCellRenderer : ListCellRenderer<DataScienceProjectTemplate> {
        private val panel = JPanel(BorderLayout(10, 0)).apply {
            border = EmptyBorder(6, 10, 6, 10)
        }
        private val iconLabel = JLabel().apply { font = font.deriveFont(20f) }
        private val titleLabel = JLabel().apply { font = font.deriveFont(Font.BOLD, 12f) }
        private val summaryLabel = JLabel().apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            foreground = Color.GRAY
        }

        init {
            val center = JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                isOpaque = false
                add(titleLabel)
                add(Box.createVerticalStrut(2))
                add(summaryLabel)
            }
            panel.add(iconLabel, BorderLayout.WEST)
            panel.add(center, BorderLayout.CENTER)
        }

        override fun getListCellRendererComponent(
            list: JList<out DataScienceProjectTemplate>,
            value: DataScienceProjectTemplate?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            if (value != null) {
                iconLabel.text = value.iconEmoji
                titleLabel.text = value.displayName
                summaryLabel.text = value.summary
            }
            panel.background = if (isSelected) Color(238, 232, 213) else Color.WHITE
            return panel
        }
    }
}
