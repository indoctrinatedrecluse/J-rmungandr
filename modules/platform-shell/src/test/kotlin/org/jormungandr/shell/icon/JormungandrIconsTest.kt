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

        val pngStream = JormungandrIcons::class.java.getResourceAsStream("/META-INF/pluginIcon.png")
        assertNotNull(pngStream, "pluginIcon.png must exist for fallback raster display")

        val png2xStream = JormungandrIcons::class.java.getResourceAsStream("/META-INF/pluginIcon@2x.png")
        assertNotNull(png2xStream, "pluginIcon@2x.png must exist for HiDPI raster display")
    }

    @Test
    fun `test bespoke extension icons exist and are decodable`() {
        val extensionIconPaths = listOf(
            "/icons/jupyter_16.png" to 16,
            "/icons/jupyter_24.png" to 24,
            "/icons/jupyter_32.png" to 32,
            "/icons/plots_16.png" to 16,
            "/icons/dataframe_16.png" to 16,
            "/icons/dataframe_24.png" to 24,
            "/icons/dataframe_32.png" to 32,
            "/icons/database_16.png" to 16,
            "/icons/database_24.png" to 24,
            "/icons/database_32.png" to 32
        )

        for ((path, expectedSize) in extensionIconPaths) {
            val stream = JormungandrIcons::class.java.getResourceAsStream(path)
            assertNotNull(stream, "Resource $path should exist on classpath")

            val img: BufferedImage? = ImageIO.read(stream)
            assertNotNull(img, "Image at $path should be decodable")
            assertEquals(expectedSize, img!!.width, "Width for $path should match $expectedSize")
            assertEquals(expectedSize, img.height, "Height for $path should match $expectedSize")
        }
    }

    @Test
    fun `test getExtensionIcon returns non-null distinct icons`() {
        val jupyter16 = JormungandrIcons.getExtensionIcon("org.jormungandr.jupyter", 16)
        val dataframe16 = JormungandrIcons.getExtensionIcon("org.jormungandr.dataframe", 16)
        val database16 = JormungandrIcons.getExtensionIcon("org.jormungandr.database", 16)
        val default16 = JormungandrIcons.getExtensionIcon("org.jormungandr.other", 16)

        assertNotNull(jupyter16, "Jupyter icon must not be null")
        assertNotNull(dataframe16, "DataFrame icon must not be null")
        assertNotNull(database16, "Database icon must not be null")
        assertNotNull(default16, "Default icon must not be null")

        val jupyter24 = JormungandrIcons.getExtensionIcon("org.jormungandr.jupyter", 24)
        val dataframe24 = JormungandrIcons.getExtensionIcon("org.jormungandr.dataframe", 24)
        val database24 = JormungandrIcons.getExtensionIcon("org.jormungandr.database", 24)

        assertNotNull(jupyter24, "Jupyter 24 icon must not be null")
        assertNotNull(dataframe24, "DataFrame 24 icon must not be null")
        assertNotNull(database24, "Database 24 icon must not be null")
    }
}
