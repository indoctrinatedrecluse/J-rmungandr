package org.jormungandr.shell.icon

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class JormungandrIconsTest {

    @Test
    fun `test all required icon resolutions exist and are loadable`() {
        val requiredSizes = listOf(16, 24, 32, 48, 64, 128, 256)

        for (size in requiredSizes) {
            val resourcePath = "/icons/jormungandr_$size.png"
            val stream = JormungandrIcons::class.java.getResourceAsStream(resourcePath)
            assertNotNull(stream, "Resource $resourcePath should exist on classpath")

            val image: BufferedImage? = ImageIO.read(stream)
            assertNotNull(image, "Image at $resourcePath should be decodable")
            assertEquals(size, image!!.width, "Width for $resourcePath should match $size")
            assertEquals(size, image.height, "Height for $resourcePath should match $size")
        }
    }

    @Test
    fun `test getIconImages returns non-empty collection of images for window branding`() {
        val iconImages = JormungandrIcons.getIconImages()
        assertFalse(iconImages.isEmpty(), "Window icon list should not be empty")
        assertTrue(iconImages.size >= 5, "Should supply at least 5 resolution mipmaps for window title bars")
    }

    @Test
    fun `test multi-resolution Windows ICO exists and contains data`() {
        val icoStream = JormungandrIcons::class.java.getResourceAsStream("/icons/jormungandr.ico")
        assertNotNull(icoStream, "/icons/jormungandr.ico should exist in resources")
        val bytes = icoStream!!.readBytes()
        assertTrue(bytes.size > 1000, "ICO file should contain multi-resolution image data (size was ${bytes.size} bytes)")
        // Verify ICO magic header: 0x0000 0x0001 (reserved 0, type 1 for icon)
        assertEquals(0, bytes[0].toInt())
        assertEquals(0, bytes[1].toInt())
        assertEquals(1, bytes[2].toInt())
        assertEquals(0, bytes[3].toInt())
    }

    @Test
    fun `test pluginIcon svg files exist in META-INF`() {
        val svgStream = JormungandrIcons::class.java.getResourceAsStream("/META-INF/pluginIcon.svg")
        assertNotNull(svgStream, "pluginIcon.svg must exist for IntelliJ Platform plugin manager")

        val svgDarkStream = JormungandrIcons::class.java.getResourceAsStream("/META-INF/pluginIcon_dark.svg")
        assertNotNull(svgDarkStream, "pluginIcon_dark.svg must exist for dark mode UI")
    }
}
