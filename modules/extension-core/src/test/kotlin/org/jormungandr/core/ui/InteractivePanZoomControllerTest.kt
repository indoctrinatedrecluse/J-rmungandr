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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.Point
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.JPanel

class InteractivePanZoomControllerTest {

    @Test
    fun testPanZoomLifecycle() {
        val panel = JPanel()
        val controller = InteractivePanZoomController(panel)

        assertEquals(1.0, controller.scale)
        assertEquals(0.0, controller.offsetX)
        assertEquals(0.0, controller.offsetY)

        // Simulate Zoom In (Wheel rotation -1)
        val zoomInEvent = MouseWheelEvent(
            panel, MouseWheelEvent.MOUSE_WHEEL, System.currentTimeMillis(),
            0, 100, 100, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, -1
        )
        controller.mouseWheelMoved(zoomInEvent)
        assertTrue(controller.scale > 1.0, "Scale should increase after zoom in")

        // Simulate Drag
        val pressEvent = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 50, 50, 1, false)
        controller.mousePressed(pressEvent)

        val dragEvent = MouseEvent(panel, MouseEvent.MOUSE_DRAGGED, System.currentTimeMillis(), 0, 80, 90, 1, false)
        controller.mouseDragged(dragEvent)
        assertTrue(controller.offsetX != 0.0 || controller.offsetY != 0.0, "Offsets should change after drag")

        // Simulate Double Click Reset
        val dblClickEvent = MouseEvent(panel, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 50, 50, 2, false)
        controller.mousePressed(dblClickEvent)

        assertEquals(1.0, controller.scale, "Double click must reset scale to 1.0")
        assertEquals(0.0, controller.offsetX, "Double click must reset offsetX to 0.0")
        assertEquals(0.0, controller.offsetY, "Double click must reset offsetY to 0.0")
    }
}
