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

package org.jormungandr.core.ui

import java.awt.Graphics2D
import java.awt.Point
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import java.awt.geom.AffineTransform
import javax.swing.JComponent
import kotlin.math.max
import kotlin.math.min

/**
 * Universal interactive Pan & Zoom controller for Swing 2D graphics canvases.
 * Features:
 * - Mouse wheel zooming focused directly around cursor coordinates.
 * - Click-and-drag panning.
 * - Double-click to reset scale (1.0x) and origin (0, 0).
 * - Applies transform cleanly to Graphics2D rendering context.
 */
class InteractivePanZoomController(
    private val component: JComponent,
    var minScale: Double = 0.2,
    var maxScale: Double = 6.0,
    var zoomStep: Double = 1.15
) : MouseAdapter() {

    var scale: Double = 1.0
        private set
    var offsetX: Double = 0.0
        private set
    var offsetY: Double = 0.0
        private set

    private var dragStart: Point? = null

    init {
        component.addMouseListener(this)
        component.addMouseMotionListener(this)
        component.addMouseWheelListener(this)
    }

    fun applyTransform(g2: Graphics2D): AffineTransform {
        val oldTx = g2.transform
        g2.translate(offsetX, offsetY)
        g2.scale(scale, scale)
        return oldTx
    }

    fun reset() {
        scale = 1.0
        offsetX = 0.0
        offsetY = 0.0
        component.repaint()
    }

    override fun mouseWheelMoved(e: MouseWheelEvent) {
        val mousePt = e.point
        val oldScale = scale
        val newScale = if (e.wheelRotation < 0) {
            min(maxScale, oldScale * zoomStep)
        } else {
            max(minScale, oldScale / zoomStep)
        }

        if (newScale != oldScale) {
            val factor = newScale / oldScale
            offsetX = mousePt.x - (mousePt.x - offsetX) * factor
            offsetY = mousePt.y - (mousePt.y - offsetY) * factor
            scale = newScale
            component.repaint()
        }
    }

    override fun mousePressed(e: MouseEvent) {
        if (e.clickCount == 2) {
            reset()
            return
        }
        dragStart = e.point
    }

    override fun mouseDragged(e: MouseEvent) {
        val start = dragStart ?: return
        val dx = e.point.x - start.x
        val dy = e.point.y - start.y
        offsetX += dx
        offsetY += dy
        dragStart = e.point
        component.repaint()
    }

    override fun mouseReleased(e: MouseEvent) {
        dragStart = null
    }
}
