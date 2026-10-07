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

package org.jormungandr.jupyter.ui.render

import com.google.gson.*
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import java.awt.*
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.border.CompoundBorder
import javax.swing.border.EmptyBorder
import javax.swing.border.LineBorder
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Data payload stored inside a JSON tree node for syntax-highlighted rendering and path tracking.
 */
data class JsonNodeData(
    val key: String?,
    val element: JsonElement,
    val path: String
) {
    override fun toString(): String {
        val prefix = if (key != null) "$key: " else ""
        return when {
            element.isJsonObject -> "$prefix{ ${element.asJsonObject.size()} fields }"
            element.isJsonArray -> "$prefix[ ${element.asJsonArray.size()} items ]"
            element.isJsonNull -> "${prefix}null"
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isString -> "$prefix\"${prim.asString}\""
                    prim.isNumber -> "$prefix${prim.asNumber}"
                    prim.isBoolean -> "$prefix${prim.asBoolean}"
                    else -> "$prefix${prim.asString}"
                }
            }
            else -> "$prefix$element"
        }
    }
}

/**
 * Interactive Collapsible JSON Tree Inspector Card for Jupyter Notebook outputs:
 * - Full JSON Tree navigation with expandable objects and arrays
 * - Syntax color-coded keys, strings, numbers, booleans, and nulls
 * - Live key/value search filter
 * - Expand All / Collapse All controls
 * - 1-Click JSON Copy & Path Copy (`users[0].name`)
 */
class NotebookJsonTreeCard(
    private val rawJson: String,
    private val execCount: Int? = null
) : JPanel(BorderLayout(0, 6)) {

    private val searchField = JBTextField(16).apply {
        emptyText.text = "🔍 Filter keys or values..."
    }
    private val tree: JTree
    private val treeModel: DefaultTreeModel
    private val rootNode: DefaultMutableTreeNode

    init {
        isOpaque = true
        background = Color(255, 255, 255)
        border = CompoundBorder(
            LineBorder(Color(203, 213, 225), 1, true),
            EmptyBorder(6, 8, 8, 8)
        )

        // Parse JSON
        val parsedElement = runCatching {
            JsonParser.parseString(rawJson)
        }.getOrElse {
            JsonPrimitive(rawJson)
        }

        rootNode = buildTreeNode(null, parsedElement, "$")
        treeModel = DefaultTreeModel(rootNode)
        tree = JTree(treeModel).apply {
            isRootVisible = true
            showsRootHandles = true
            cellRenderer = JsonTreeRenderer()
            rowHeight = 22
        }

        // Expand first 2 levels
        expandTreeLevels(tree, 2)

        setupUI()
    }

    private fun setupUI() {
        // Header Toolbar
        val topPanel = JPanel(BorderLayout(6, 0)).apply { isOpaque = false }
        val titleText = if (execCount != null) "📦 JSON Inspector [Out $execCount]" else "📦 Interactive JSON Inspector"
        val titleLabel = JBLabel(titleText).apply {
            font = font.deriveFont(Font.BOLD, 11f)
            foreground = Color(30, 41, 59)
        }

        val leftHeader = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0)).apply { isOpaque = false }
        leftHeader.add(titleLabel)
        leftHeader.add(searchField)

        val rightHeader = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply { isOpaque = false }
        val expandBtn = JButton("➕ Expand").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener { expandAll(tree) }
        }
        val collapseBtn = JButton("➖ Collapse").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener { collapseAll(tree) }
        }
        val copyJsonBtn = JButton("📋 Copy JSON").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                val pretty = runCatching {
                    GsonBuilder().setPrettyPrinting().create().toJson(JsonParser.parseString(rawJson))
                }.getOrDefault(rawJson)
                val sel = StringSelection(pretty)
                Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
            }
        }
        val copyPathBtn = JButton("📋 Copy Path").apply {
            font = font.deriveFont(Font.PLAIN, 10f)
            isFocusable = false
            addActionListener {
                val selPath = tree.selectionPath?.lastPathComponent as? DefaultMutableTreeNode
                val data = selPath?.userObject as? JsonNodeData
                if (data != null) {
                    val sel = StringSelection(data.path)
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                }
            }
        }

        rightHeader.add(expandBtn)
        rightHeader.add(collapseBtn)
        rightHeader.add(copyJsonBtn)
        rightHeader.add(copyPathBtn)

        topPanel.add(leftHeader, BorderLayout.WEST)
        topPanel.add(rightHeader, BorderLayout.EAST)
        add(topPanel, BorderLayout.NORTH)

        // Tree scroll pane
        val scrollPane = JBScrollPane(tree).apply {
            border = LineBorder(Color(226, 232, 240), 1)
            preferredSize = Dimension(preferredSize.width, 240)
        }
        add(scrollPane, BorderLayout.CENTER)

        // Search Filter Interaction
        searchField.addKeyListener(object : KeyAdapter() {
            override fun keyReleased(e: KeyEvent?) {
                applyFilter(searchField.text.trim())
            }
        })
    }

    private fun buildTreeNode(key: String?, element: JsonElement, currentPath: String): DefaultMutableTreeNode {
        val data = JsonNodeData(key, element, currentPath)
        val node = DefaultMutableTreeNode(data)

        when {
            element.isJsonObject -> {
                val obj = element.asJsonObject
                for ((childKey, childElem) in obj.entrySet()) {
                    val nextPath = if (currentPath == "$") childKey else "$currentPath.$childKey"
                    node.add(buildTreeNode(childKey, childElem, nextPath))
                }
            }
            element.isJsonArray -> {
                val arr = element.asJsonArray
                for (i in 0 until arr.size()) {
                    val nextPath = "$currentPath[$i]"
                    node.add(buildTreeNode("[$i]", arr[i], nextPath))
                }
            }
        }
        return node
    }

    private fun applyFilter(query: String) {
        if (query.isBlank()) {
            treeModel.setRoot(rootNode)
            expandTreeLevels(tree, 2)
            return
        }

        val filteredRoot = filterNode(rootNode, query.lowercase())
        if (filteredRoot != null) {
            treeModel.setRoot(filteredRoot)
            expandAll(tree)
        } else {
            val empty = DefaultMutableTreeNode(JsonNodeData(null, JsonPrimitive("No matches for '$query'"), "$"))
            treeModel.setRoot(empty)
        }
    }

    private fun filterNode(node: DefaultMutableTreeNode, query: String): DefaultMutableTreeNode? {
        val data = node.userObject as? JsonNodeData ?: return null
        val selfMatches = data.toString().lowercase().contains(query)

        val matchingChildren = mutableListOf<DefaultMutableTreeNode>()
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            val filteredChild = filterNode(child, query)
            if (filteredChild != null) {
                matchingChildren.add(filteredChild)
            }
        }

        return if (selfMatches || matchingChildren.isNotEmpty()) {
            val newNode = DefaultMutableTreeNode(data)
            for (c in matchingChildren) {
                newNode.add(c)
            }
            newNode
        } else null
    }

    private fun expandAll(tree: JTree) {
        var i = 0
        while (i < tree.rowCount) {
            tree.expandRow(i)
            i++
        }
    }

    private fun collapseAll(tree: JTree) {
        var i = tree.rowCount - 1
        while (i > 0) {
            tree.collapseRow(i)
            i--
        }
    }

    private fun expandTreeLevels(tree: JTree, level: Int) {
        fun expand(node: DefaultMutableTreeNode, currentLevel: Int) {
            if (currentLevel >= level) return
            tree.expandPath(TreePath(node.path))
            for (i in 0 until node.childCount) {
                val c = node.getChildAt(i) as DefaultMutableTreeNode
                expand(c, currentLevel + 1)
            }
        }
        expand(rootNode, 0)
    }

    private class JsonTreeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree,
            value: Any?,
            sel: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean
        ): Component {
            val comp = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus) as JLabel
            val node = value as? DefaultMutableTreeNode ?: return comp
            val data = node.userObject as? JsonNodeData ?: return comp

            comp.font = Font("Consolas", Font.PLAIN, 12)
            val keyPrefix = if (data.key != null) "<b style=\"color:#2563eb;\">${data.key}</b>: " else ""

            val valueHtml = when {
                data.element.isJsonObject -> "<span style=\"color:#64748b;\">{ ${data.element.asJsonObject.size()} fields }</span>"
                data.element.isJsonArray -> "<span style=\"color:#64748b;\">[ ${data.element.asJsonArray.size()} items ]</span>"
                data.element.isJsonNull -> "<span style=\"color:#94a3b8; font-style:italic;\">null</span>"
                data.element.isJsonPrimitive -> {
                    val prim = data.element.asJsonPrimitive
                    when {
                        prim.isString -> "<span style=\"color:#16a34a;\">\"${prim.asString}\"</span>"
                        prim.isNumber -> "<span style=\"color:#d97706;\">${prim.asNumber}</span>"
                        prim.isBoolean -> "<span style=\"color:#7c3aed; font-weight:bold;\">${prim.asBoolean}</span>"
                        else -> "<span style=\"color:#0f172a;\">${prim.asString}</span>"
                    }
                }
                else -> data.element.toString()
            }

            comp.text = "<html>$keyPrefix$valueHtml</html>"
            return comp
        }
    }
}
