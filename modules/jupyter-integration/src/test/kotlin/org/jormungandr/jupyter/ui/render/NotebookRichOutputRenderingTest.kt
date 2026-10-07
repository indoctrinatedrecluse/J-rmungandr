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

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.Base64

class NotebookRichOutputRenderingTest {

    @Test
    fun `test notebook math renderer translates latex expressions to styled html`() {
        val latex = """\sigma(z) = \frac{1}{1 + e^{-z}}"""
        val html = NotebookMathRenderer.renderLatexToHtml(latex)

        assertTrue(html.contains("σ(z)"))
        assertTrue(html.contains("<table"))
        assertTrue(html.contains("<sup>-z</sup>"))
    }

    @Test
    fun `test notebook math renderer translates complex operators and greek symbols`() {
        val formula = """\int_{0}^{\infty} \sum_{i=1}^{n} \alpha_i \sqrt{x} dx \approx \pi"""
        val html = NotebookMathRenderer.renderLatexToHtml(formula)

        assertTrue(html.contains("∫"))
        assertTrue(html.contains("∞"))
        assertTrue(html.contains("∑"))
        assertTrue(html.contains("α"))
        assertTrue(html.contains("√"))
        assertTrue(html.contains("≈"))
        assertTrue(html.contains("π"))
    }

    @Test
    fun `test mathjax standalone html generation`() {
        val formula = """MSE = \frac{1}{n}\sum_{i=1}^n(y_i - \hat{y}_i)^2"""
        val standalone = NotebookMathRenderer.generateStandaloneMathJaxHtml(formula, isBlock = true)

        assertTrue(standalone.contains("MathJax"))
        assertTrue(standalone.contains("tex-mml-chtml.js"))
        assertTrue(standalone.contains("$$$formula$$$"))
    }

    @Test
    fun `test create latex card component`() {
        val card = NotebookMathRenderer.createLatexCard("""E = mc^2""", execCount = 3)
        assertNotNull(card)
        assertEquals(2, card.componentCount)
    }

    @Test
    fun `test interactive json tree card parses and structures nodes`() {
        val json = """
        {
            "status": "success",
            "model": "ResNet-50",
            "accuracy": 0.942,
            "trained": true,
            "metrics": {
                "loss": 0.058,
                "epochs": [1, 2, 3]
            },
            "notes": null
        }
        """.trimIndent()

        val card = NotebookJsonTreeCard(json, execCount = 5)
        assertNotNull(card)
        assertEquals(2, card.componentCount)
    }

    @Test
    fun `test audio player card parsing and duration estimation`() {
        val fakeWavBytes = ByteArray(44100 * 2 * 2) { 0 } // 1 second of 44.1kHz 16-bit stereo
        val base64Data = "data:audio/wav;base64," + Base64.getEncoder().encodeToString(fakeWavBytes)

        val parsed = NotebookAudioPlayerCard.parseAudioBytes(base64Data)
        assertEquals(fakeWavBytes.size, parsed.size)

        val duration = NotebookAudioPlayerCard.estimateDurationMs(parsed, "audio/wav")
        assertTrue(duration in 950..1050)

        val timeStr = NotebookAudioPlayerCard.formatTime(125000L)
        assertEquals("02:05", timeStr)

        val card = NotebookAudioPlayerCard(base64Data, "audio/wav", 2)
        assertNotNull(card)
    }

    @Test
    fun `test video player card component initialization`() {
        val fakeVideoBytes = "MP4_HEADER_TEST_BYTES".toByteArray()
        val base64Data = "data:video/mp4;base64," + Base64.getEncoder().encodeToString(fakeVideoBytes)

        val card = NotebookVideoPlayerCard(base64Data, "video/mp4", 4)
        assertNotNull(card)
        assertEquals(3, card.componentCount) // NORTH, CENTER, SOUTH
    }
}
