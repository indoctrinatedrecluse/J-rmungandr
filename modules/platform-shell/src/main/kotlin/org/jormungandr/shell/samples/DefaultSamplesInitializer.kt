package org.jormungandr.shell.samples

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.ProjectManager
import java.io.File

/**
 * Initializes and bundles the default samples workspace on IDE first-run.
 *
 * Ensures:
 * 1. The bundled `samples/` directory is automatically opened on first launch when no prior project exists.
 * 2. Opening the project automatically registers it into IntelliJ's Recent Projects list.
 * 3. Standard IntelliJ lifecycle naturally preserves and restores whichever project is subsequently opened.
 */
object DefaultSamplesInitializer {
    private val LOG = Logger.getInstance(DefaultSamplesInitializer::class.java)

    fun locateSamplesDirectory(): File? {
        val home = PathManager.getHomePath()
        val candidates = listOf(
            File(home, "samples"),
            File(home, "../samples"),
            File(System.getProperty("user.dir") ?: "", "samples"),
            File("D:/Projects/Jörmungandr/samples")
        )
        return candidates.firstOrNull { it.exists() && it.isDirectory }
    }

    fun initializeDefaultSamples() {
        val samplesDir = locateSamplesDirectory() ?: run {
            LOG.info("No bundled samples directory located.")
            return
        }

        try {
            val recentManager = RecentProjectsManagerBase.getInstanceEx()
            val recentPaths = recentManager.getRecentPaths()

            val samplesPathStr = samplesDir.canonicalPath.replace('\\', '/')

            // First run check: if no projects exist in the recent projects history
            if (recentPaths.isEmpty()) {
                LOG.info("First run detected: scheduling default samples workspace to open: $samplesPathStr")
                ApplicationManager.getApplication().invokeLater({
                    if (ProjectManager.getInstance().openProjects.isEmpty()) {
                        LOG.info("Automatically opening default samples workspace: $samplesPathStr")
                        ProjectUtil.openOrImport(samplesDir.toPath(), null, false)
                    }
                }, ModalityState.nonModal())
            }
        } catch (e: Throwable) {
            LOG.warn("Failed to initialize default samples project", e)
        }
    }
}
