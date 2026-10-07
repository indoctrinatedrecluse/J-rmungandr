package org.jormungandr.shell.project

import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Options configuring the project scaffolding execution.
 */
data class ScaffoldOptions(
    val projectName: String,
    val targetDirectory: File,
    val template: DataScienceProjectTemplate,
    val environmentType: EnvironmentType = EnvironmentType.UV,
    val initGit: Boolean = true,
    val autoCreateVenv: Boolean = false
)

/**
 * Result of the project scaffolding execution.
 */
data class ScaffoldResult(
    val success: Boolean,
    val message: String,
    val projectDir: File,
    val primaryFile: File? = null,
    val filesCreated: Int = 0
)

/**
 * High-performance project scaffolding engine for data science, analytics, and machine learning workspaces.
 */
object DataScienceProjectScaffolder {

    private val LOG = Logger.getInstance(DataScienceProjectScaffolder::class.java)

    /**
     * Executes the project scaffolding according to the provided [ScaffoldOptions].
     */
    fun scaffold(options: ScaffoldOptions): ScaffoldResult {
        val projectDir = options.targetDirectory
        if (!projectDir.exists()) {
            val created = projectDir.mkdirs()
            if (!created) {
                return ScaffoldResult(
                    success = false,
                    message = "Failed to create directory: ${projectDir.absolutePath}",
                    projectDir = projectDir
                )
            }
        }

        var filesCount = 0
        var primaryFile: File? = null

        try {
            // 1. Write template files
            val templateFiles = options.template.generateFiles(options.projectName)
            for (tf in templateFiles) {
                val destFile = File(projectDir, tf.relativePath)
                destFile.parentFile?.mkdirs()
                if (tf.content.isNotEmpty()) {
                    destFile.writeText(tf.content, Charsets.UTF_8)
                } else if (!destFile.exists()) {
                    destFile.createNewFile()
                }
                filesCount++
                if (tf.isPrimaryToOpen && primaryFile == null) {
                    primaryFile = destFile
                }
            }

            // 2. Write environment provisioning helper files
            val envFiles = options.environmentType.generateEnvironmentFiles(
                options.projectName,
                options.template.defaultDependencies
            )
            for (ef in envFiles) {
                val destFile = File(projectDir, ef.relativePath)
                destFile.parentFile?.mkdirs()
                destFile.writeText(ef.content, Charsets.UTF_8)
                filesCount++
            }

            // 3. Initialize Git repository if requested
            if (options.initGit) {
                tryInitGit(projectDir)
            }

            // 4. Provision virtual environment if requested
            if (options.autoCreateVenv && options.environmentType != EnvironmentType.MANUAL) {
                tryProvisionVenv(projectDir, options.environmentType)
            }

            return ScaffoldResult(
                success = true,
                message = "Successfully created '${options.projectName}' with ${options.template.displayName} template.",
                projectDir = projectDir,
                primaryFile = primaryFile,
                filesCreated = filesCount
            )
        } catch (e: Exception) {
            LOG.warn("Error during project scaffolding: ${e.message}", e)
            return ScaffoldResult(
                success = false,
                message = "Error scaffolding project: ${e.message}",
                projectDir = projectDir,
                primaryFile = primaryFile,
                filesCreated = filesCount
            )
        }
    }

    private fun tryInitGit(projectDir: File) {
        try {
            val process = ProcessBuilder("git", "init")
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(10, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                LOG.info("Git repository initialized in ${projectDir.absolutePath}")
            }
        } catch (e: Exception) {
            LOG.warn("Git initialization skipped or unavailable: ${e.message}")
        }
    }

    private fun tryProvisionVenv(projectDir: File, envType: EnvironmentType) {
        try {
            val commands = when (envType) {
                EnvironmentType.UV -> listOf("uv", "venv", ".venv")
                EnvironmentType.VENV -> listOf("python", "-m", "venv", ".venv")
                else -> return
            }

            val process = ProcessBuilder(commands)
                .directory(projectDir)
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(15, TimeUnit.SECONDS)
            if (finished && process.exitValue() == 0) {
                LOG.info("Virtual environment successfully created in ${projectDir.absolutePath}/.venv")
            }
        } catch (e: Exception) {
            LOG.warn("Virtual environment creation skipped: ${e.message}")
        }
    }
}
