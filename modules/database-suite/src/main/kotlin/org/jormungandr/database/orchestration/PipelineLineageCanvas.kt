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

package org.jormungandr.database.orchestration

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import java.awt.*
import java.awt.event.*
import java.awt.geom.CubicCurve2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import java.io.File
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.max
import kotlin.math.min

class PipelineLineageCanvas(
    private val project: Project,
    var onNodeSelected: ((PipelineNode?) -> Unit)? = null
) : JPanel() {

    private var graph: PipelineGraph = PipelineGraph()
    private var selectedNodeId: String? = null
    private var hoveredNodeId: String? = null
    private var filterQuery: String = ""

    // Pan & Zoom
    private var zoomFactor: Double = 1.0
    private var offsetX: Double = 50.0
    private var offsetY: Double = 50.0
    private var lastDragPoint: Point? = null

    // Layout metrics
    private val cardWidth = 195
    private val cardHeight = 64
    private val layerSpacingX = 260
    private val nodeSpacingY = 95

    init {
        isFocusable = true
        background = JBColor(Color(248, 250, 252), Color(15, 23, 42))

        val mouseAdapter = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                lastDragPoint = e.point
                val clickedNode = findNodeAt(e.x, e.y)
                if (clickedNode != null) {
                    selectedNodeId = clickedNode.id
                    onNodeSelected?.invoke(clickedNode)
                    if (e.clickCount == 2) {
                        openNodeInEditor(clickedNode)
                    }
                } else {
                    selectedNodeId = null
                    onNodeSelected?.invoke(null)
                }
                repaint()
            }

            override fun mouseReleased(e: MouseEvent) {
                lastDragPoint = null
            }

            override fun mouseMoved(e: MouseEvent) {
                val node = findNodeAt(e.x, e.y)
                val newHoverId = node?.id
                if (newHoverId != hoveredNodeId) {
                    hoveredNodeId = newHoverId
                    cursor = if (newHoverId != null) Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) else Cursor.getDefaultCursor()
                    repaint()
                }
            }

            override fun mouseDragged(e: MouseEvent) {
                val last = lastDragPoint
                if (last != null) {
                    val dx = e.x - last.x
                    val dy = e.y - last.y
                    offsetX += dx
                    offsetY += dy
                    lastDragPoint = e.point
                    repaint()
                }
            }

            override fun mouseWheelMoved(e: MouseWheelEvent) {
                val rot = e.preciseWheelRotation
                val factor = if (rot < 0) 1.1 else 0.9
                val newZoom = (zoomFactor * factor).coerceIn(0.35, 2.5)

                // Zoom relative to cursor point
                val mouseX = e.x
                val mouseY = e.y
                offsetX = mouseX - (mouseX - offsetX) * (newZoom / zoomFactor)
                offsetY = mouseY - (mouseY - offsetY) * (newZoom / zoomFactor)
                zoomFactor = newZoom
                repaint()
            }
        }

        addMouseListener(mouseAdapter)
        addMouseMotionListener(mouseAdapter)
        addMouseWheelListener(mouseAdapter)
    }

    fun setGraph(newGraph: PipelineGraph) {
        this.graph = newGraph
        calculateGraphLayout()
        resetView()
        repaint()
    }

    fun setFilter(query: String) {
        this.filterQuery = query.trim().lowercase()
        repaint()
    }

    fun resetView() {
        zoomFactor = 1.0
        offsetX = 60.0
        offsetY = 60.0
        repaint()
    }

    fun zoomIn() {
        zoomFactor = (zoomFactor * 1.2).coerceAtMost(2.5)
        repaint()
    }

    fun zoomOut() {
        zoomFactor = (zoomFactor * 0.8).coerceAtLeast(0.35)
        repaint()
    }

    private fun calculateGraphLayout() {
        graph.calculateLayers()

        val layerMap = mutableMapOf<Int, MutableList<PipelineNode>>()
        graph.nodes.values.forEach { node ->
            layerMap.computeIfAbsent(node.layer) { mutableListOf() }.add(node)
        }

        layerMap.forEach { (layer, nodesInLayer) ->
            nodesInLayer.sortBy { it.name }
            val startY = 40
            nodesInLayer.forEachIndexed { index, node ->
                node.x = layer * layerSpacingX + 40
                node.y = startY + index * nodeSpacingY
            }
        }
    }

    private fun findNodeAt(screenX: Int, screenY: Int): PipelineNode? {
        val worldX = (screenX - offsetX) / zoomFactor
        val worldY = (screenY - offsetY) / zoomFactor

        return graph.nodes.values.lastOrNull { node ->
            worldX >= node.x && worldX <= node.x + cardWidth &&
                    worldY >= node.y && worldY <= node.y + cardHeight
        }
    }

    private fun openNodeInEditor(node: PipelineNode) {
        val path = node.filePath ?: return
        val vFile = LocalFileSystem.getInstance().findFileByIoFile(File(path)) ?: return
        OpenFileDescriptor(project, vFile, max(0, node.lineNumber - 1), 0).navigate(true)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        val g2 = g as? Graphics2D ?: return

        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)

        drawGrid(g2)

        val oldTransform = g2.transform
        g2.translate(offsetX, offsetY)
        g2.scale(zoomFactor, zoomFactor)

        val selected = selectedNodeId?.let { graph.nodes[it] }
        val highlightedUpstream = selected?.upstreamIds ?: emptySet()
        val highlightedDownstream = selected?.downstreamIds ?: emptySet()

        // 1. Draw Edges
        graph.edges.forEach { edge ->
            val src = graph.nodes[edge.sourceId] ?: return@forEach
            val tgt = graph.nodes[edge.targetId] ?: return@forEach

            val isSelectedEdge = (selected != null && (
                    (src.id == selected.id && tgt.id in highlightedDownstream) ||
                            (tgt.id == selected.id && src.id in highlightedUpstream)
                    ))

            drawEdge(g2, src, tgt, isSelectedEdge)
        }

        // 2. Draw Nodes
        graph.nodes.values.forEach { node ->
            val isSelected = node.id == selectedNodeId
            val isHovered = node.id == hoveredNodeId
            val isUpstream = node.id in highlightedUpstream
            val isDownstream = node.id in highlightedDownstream
            val matchesFilter = filterQuery.isEmpty() || node.name.lowercase().contains(filterQuery)

            drawNodeCard(g2, node, isSelected, isHovered, isUpstream, isDownstream, matchesFilter)
        }

        g2.transform = oldTransform
    }

    private fun drawGrid(g2: Graphics2D) {
        val gridStep = (30 * zoomFactor).toInt().coerceAtLeast(15)
        val dotColor = JBColor(Color(226, 232, 240, 160), Color(51, 65, 85, 120))
        g2.color = dotColor

        val startX = (offsetX % gridStep).toInt()
        val startY = (offsetY % gridStep).toInt()

        for (x in startX until width step gridStep) {
            for (y in startY until height step gridStep) {
                g2.fillRect(x, y, 2, 2)
            }
        }
    }

    private fun drawEdge(g2: Graphics2D, src: PipelineNode, tgt: PipelineNode, isHighlighted: Boolean) {
        val x1 = (src.x + cardWidth).toDouble()
        val y1 = (src.y + cardHeight / 2).toDouble()
        val x2 = tgt.x.toDouble()
        val y2 = (tgt.y + cardHeight / 2).toDouble()

        val dx = (x2 - x1).coerceAtLeast(40.0)
        val ctrl1X = x1 + dx * 0.5
        val ctrl1Y = y1
        val ctrl2X = x2 - dx * 0.5
        val ctrl2Y = y2

        val curve = CubicCurve2D.Double(x1, y1, ctrl1X, ctrl1Y, ctrl2X, ctrl2Y, x2, y2)

        if (isHighlighted) {
            g2.color = Color(56, 189, 248, 230)
            g2.stroke = BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        } else {
            g2.color = JBColor(Color(148, 163, 184, 140), Color(100, 116, 139, 120))
            g2.stroke = BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        }
        g2.draw(curve)

        // Arrow head
        drawArrowHead(g2, x2, y2, isHighlighted)
    }

    private fun drawArrowHead(g2: Graphics2D, x: Double, y: Double, isHighlighted: Boolean) {
        val arrowSize = if (isHighlighted) 6.0 else 4.5
        val path = Path2D.Double().apply {
            moveTo(x, y)
            lineTo(x - arrowSize * 1.5, y - arrowSize)
            lineTo(x - arrowSize * 1.5, y + arrowSize)
            closePath()
        }
        g2.fill(path)
    }

    private fun drawNodeCard(
        g2: Graphics2D,
        node: PipelineNode,
        isSelected: Boolean,
        isHovered: Boolean,
        isUpstream: Boolean,
        isDownstream: Boolean,
        matchesFilter: Boolean
    ) {
        val rect = RoundRectangle2D.Double(node.x.toDouble(), node.y.toDouble(), cardWidth.toDouble(), cardHeight.toDouble(), 12.0, 12.0)

        // Background
        val baseBg = when {
            isSelected -> JBColor(Color(238, 242, 255), Color(30, 41, 59))
            isUpstream -> JBColor(Color(239, 246, 255), Color(23, 37, 84))
            isDownstream -> JBColor(Color(240, 253, 244), Color(20, 83, 45))
            isHovered -> JBColor(Color(241, 245, 249), Color(51, 65, 85))
            else -> JBColor(Color(255, 255, 255), Color(30, 41, 59))
        }

        g2.color = if (matchesFilter) baseBg else JBColor(Color(245, 245, 245, 120), Color(30, 41, 59, 120))
        g2.fill(rect)

        // Border & Glow
        val borderColor = when {
            isSelected -> Color(99, 102, 241) // Indigo
            isUpstream -> Color(59, 130, 246) // Blue
            isDownstream -> Color(34, 197, 94) // Emerald
            isHovered -> Color(148, 163, 184)
            else -> JBColor(Color(226, 232, 240), Color(71, 85, 105))
        }

        val strokeWidth = if (isSelected || isUpstream || isDownstream) 2.2f else 1.0f
        g2.stroke = BasicStroke(strokeWidth)
        g2.color = borderColor
        g2.draw(rect)

        // Top accent line / badge
        val badgeColor = Color.decode(node.type.badgeColorHex)
        g2.color = badgeColor
        g2.fillRoundRect(node.x + 8, node.y + 8, 8, 8, 4, 4)

        // Node Type text
        g2.font = Font("Segoe UI", Font.BOLD, 10)
        g2.color = badgeColor
        g2.drawString(node.type.displayName.uppercase(), node.x + 22, node.y + 16)

        // Status indicator dot
        val statusColor = Color.decode(node.status.colorHex)
        g2.color = statusColor
        g2.fillOval(node.x + cardWidth - 18, node.y + 9, 8, 8)

        // Node Name
        g2.font = Font("Segoe UI", Font.BOLD, 12)
        g2.color = JBColor(Color(15, 23, 42), Color(241, 245, 249))
        val truncatedName = if (node.name.length > 20) node.name.take(18) + "…" else node.name
        g2.drawString(truncatedName, node.x + 10, node.y + 36)

        // Subtitle / Materialization & Upstream count
        g2.font = Font("Segoe UI", Font.PLAIN, 10)
        g2.color = JBColor(Color(100, 116, 139), Color(148, 163, 184))
        val subText = "${node.materialization} • In: ${node.upstreamIds.size} Out: ${node.downstreamIds.size}"
        g2.drawString(subText, node.x + 10, node.y + 52)
    }
}
