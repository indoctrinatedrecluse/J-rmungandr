package org.jormungandr.shell.icon

import com.intellij.openapi.util.IconLoader
import java.awt.Image
import java.io.InputStream
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon

/**
 * Universal icon provider for Jörmungandr IDE.
 *
 * Exposes multi-resolution representations of the World Serpent (Jörmungandr)
 * for use in:
 * - Operating System window title bars (beside app name)
 * - Taskbar and Alt+Tab switchers
 * - IDE toolbars and menu actions
 * - Executables, shortcuts, and installers
 */
object JormungandrIcons {

    private const val ICONS_PATH = "/icons"

    /** Standard 16x16 icon for toolbars, menus, and compact UI elements */
    @JvmField
    val APP_ICON: Icon = IconLoader.getIcon("$ICONS_PATH/jormungandr_16.png", JormungandrIcons::class.java)

    /** 24x24 icon for standard tool window headers */
    @JvmField
    val LOGO_24: Icon = IconLoader.getIcon("$ICONS_PATH/jormungandr_24.png", JormungandrIcons::class.java)

    /** 32x32 icon for high-DPI menus and dialog headers */
    @JvmField
    val LOGO_32: Icon = IconLoader.getIcon("$ICONS_PATH/jormungandr_32.png", JormungandrIcons::class.java)

    /** 64x64 icon for About dialogs and welcome panels */
    @JvmField
    val LOGO_64: Icon = IconLoader.getIcon("$ICONS_PATH/jormungandr_64.png", JormungandrIcons::class.java)

    /** 128x128 high-resolution brand emblem */
    @JvmField
    val LOGO_128: Icon = IconLoader.getIcon("$ICONS_PATH/jormungandr_128.png", JormungandrIcons::class.java)

    /** Available pixel dimensions bundled in platform-shell resources */
    val AVAILABLE_RESOLUTIONS = listOf(16, 24, 32, 48, 64, 128, 256, 512)

    /**
     * Loads and returns all available icon resolutions as AWT [Image] objects.
     * This collection is assigned directly to [java.awt.Frame.setIconImages] so the OS
     * automatically selects the optimal raster resolution for window title bars, taskbars,
     * and window management switchers.
     */
    fun getIconImages(): List<Image> {
        val images = mutableListOf<Image>()
        for (size in AVAILABLE_RESOLUTIONS) {
            val resourcePath = "$ICONS_PATH/jormungandr_$size.png"
            loadImageFromResource(resourcePath)?.let { images.add(it) }
        }

        // Fallback to primary jormungandr.png if size-specific icons cannot be resolved
        if (images.isEmpty()) {
            loadImageFromResource("$ICONS_PATH/jormungandr.png")?.let { images.add(it) }
        }
        return images
    }

    /**
     * Safely reads an image resource from the classpath.
     */
    fun loadImageFromResource(resourcePath: String): Image? {
        val stream: InputStream? = JormungandrIcons::class.java.getResourceAsStream(resourcePath)
        return stream?.use {
            try {
                ImageIO.read(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}
